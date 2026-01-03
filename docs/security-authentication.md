# Seguridad y autenticación

Este documento resume cómo funciona la autenticación de Turismo API, qué datos se persisten durante el ciclo de registro e inicio de sesión y cómo consumir los endpoints públicos para registro, enrolamiento TOTP y login.

## Flujo JWT y validación

### Ejemplo de generación de token (fragmento de código)

```java
// infrastructure/driven-adapters/authenticate/AuthenticateGateway.java
public String generateJWT(String email, List<String> roles) {
    Instant now = Instant.now();
    return Jwts.builder()
            .setId(UUID.randomUUID().toString())
            .setIssuer(jwtIssuer)
            .setSubject(email)
            .claim("roles", roles)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plus(jwtTtlHours, ChronoUnit.HOURS)))
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
}
```

El método se invoca desde `generateToken`, que además persiste la sesión en caché junto con la IP (si está habilitado el binding) y la expiración configurada.

### Emisión
- `AuthenticateGateway` genera el JWT (HS256) con `jti`, `iss`, `sub` (email) y el claim `roles`, usando la clave simétrica configurada en `security.jwt.secret`. También registra la sesión en caché con fecha de expiración y, opcionalmente, la IP si está habilitado `security.session.bind-ip`.
  - La emisión ocurre en `generateToken`, que normaliza email y roles, crea el token y guarda la sesión con expiración `jwtTtlHours`.【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L37-L127】
  - El método `generateJWT` arma el token con claims y firma HS256.【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L132-L146】

### Validación y refresco
- Validación rápida: se consulta la sesión en caché y se verifica que no esté vencida; si se configuró binding de IP también se compara.【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L58-L71】
- Refresco: rota el token siempre que exista en caché, la IP coincida (si aplica) y no haya pasado la ventana de gracia `security.session.refresh-grace-minutes`; se genera un nuevo JWT con los mismos claims y se reemplaza en caché.【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L73-L129】

### Filtro de recursos protegidos
- `SecurityConfig` configura un filtro JWT (WebFlux) que solo intenta autenticar cuando hay un encabezado `Authorization: Bearer <token>`. Usa `JwtReactiveAuthenticationManager`, que parsea y valida la firma con `JwtTokenProvider` y construye las autoridades `ROLE_*` a partir del claim `roles` del token.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/security/SecurityConfig.java†L137-L214】【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/security/jwt/JwtReactiveAuthenticationManager.java†L15-L46】【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/security/jwt/JwtTokenProvider.java†L10-L35】

### Ejemplo de validación en el filtro JWT

```java
// infrastructure/entry-points/reactive-web/security/JwtTokenProvider.java
public Authentication getAuthentication(String token) {
    Claims claims = parseToken(token);
    List<SimpleGrantedAuthority> authorities =
        ((List<String>) claims.get("roles", List.class))
            .stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .toList();
    return new UsernamePasswordAuthenticationToken(claims.getSubject(), token, authorities);
}
```

El filtro aplica esto cuando encuentra `Authorization: Bearer <token>` y, si es válido, inyecta el `Authentication` en el contexto Reactor para que los endpoints protegidos conozcan el usuario y roles.

## Modelo de datos utilizado en autenticación

La autenticación y registro utilizan tres tablas principales (mapeadas con R2DBC):

- **users** (`UserData`): información base del usuario, OTP y tracking de login (campos: `id`, `full_name`, `email`, `url_avatar`, `identification_type`, `identification_number`, `otp_hash`, `otp_expires_at`, `otp_attempts`, `otp_max_attempts`, `locked_until`, `last_login_at`, `created_at`).【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/usersRepository/entity/UserData.java†L17-L57】
- **roles** (`RoleData`): catálogo de roles (`id`, `role_name` como `ADMIN`, `OWNER`, `VISITOR`).【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/usersRepository/entity/RoleData.java†L14-L20】
- **user_roles** (`UserRoleData`): relación muchos-a-muchos entre usuarios y roles (`id`, `user_id`, `role_id`).【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/usersRepository/entity/UserRoleData.java†L15-L25】

Para TOTP se reutiliza la tabla `users`: el secreto cifrado y el flag de habilitación se guardan en `totp_secret_encrypted` y `totp_enabled` mediante `AuthAdapterRepository`; estos campos se actualizan durante el enrolamiento y la confirmación.【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/authRepository/AuthAdapterRepository.java†L14-L44】

## Endpoints y flujo de uso

### 1) Registro de usuario
- **Endpoint:** `POST /api/auth/create/user`
- **Handler:** `UserHandler.createUser` consume el cuerpo `User` y delega al caso de uso para persistirlo.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/UserHandler.java†L21-L25】【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/RouterRest.java†L113-L124】
- **Resultado esperado:** usuario almacenado en `users` y relación de roles según la lógica del caso de uso (por defecto suele ser `VISITOR`).
- **Ejemplo `curl`:**

```bash
curl -X POST https://turismo-back-production.up.railway.app/api/auth/create/user \
  -H "Content-Type: application/json" \
  -d '{
        "fullName": "Ana Turista",
        "email": "ana@example.com",
        "identificationType": "CC",
        "identificationNumber": "10203040"
      }'
```

### 2) Enrolamiento TOTP (habilita MFA y prepara el login)
1. **Consultar estado** (opcional): `GET /api/auth/code/status?email=<correo>` devuelve si el usuario ya tiene TOTP habilitado.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/AuthenticateHandler.java†L53-L80】【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/RouterRest.java†L55-L65】
2. **Generar secreto y QR:** `POST /api/auth/code/setup` con `{ "email": "..." }`. Genera secreto Base32, URI `otpauth://` y un `data:image/png` para el QR, y guarda el secreto cifrado en `users.totp_secret_encrypted` dejando `totp_enabled=false`.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/AuthenticateHandler.java†L24-L51】【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/authRepository/AuthAdapterRepository.java†L14-L36】【F:domain/usecase/src/main/java/co/turismo/usecase/authenticate/AuthenticateUseCase.java†L20-L60】
3. **Confirmar primer código:** `POST /api/auth/code/confirm` con `{ "email": "...", "code": 123456 }`. Valida el TOTP; si es correcto, marca `totp_enabled=true` y reinicia intentos de OTP; si falla, incrementa `otp_attempts` y puede bloquear temporalmente al usuario.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/AuthenticateHandler.java†L82-L96】【F:domain/usecase/src/main/java/co/turismo/usecase/authenticate/AuthenticateUseCase.java†L61-L98】【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/authRepository/AuthAdapterRepository.java†L38-L44】【F:infrastructure/driven-adapters/r2dbc-postgresql/src/main/java/co/turismo/r2dbc/usersRepository/repository/UserAdapterRepository.java†L53-L72】
- **Ejemplo `curl` paso 2:**

```bash
curl -X POST https://turismo-back-production.up.railway.app/api/auth/code/setup \
  -H "Content-Type: application/json" \
  -d '{ "email": "ana@example.com" }'
```

- **Ejemplo `curl` paso 3:**

```bash
curl -X POST https://turismo-back-production.up.railway.app/api/auth/code/confirm \
  -H "Content-Type: application/json" \
  -d '{ "email": "ana@example.com", "code": 123456 }'
```

### 3) Inicio de sesión con TOTP
- **Endpoint:** `POST /api/auth/login-code`
- **Flujo:**
  1. El handler lee `email` y `totpCode`, obtiene la IP del cliente y delega en `AuthenticateUseCase.authenticateTotp`.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/AuthenticateHandler.java†L98-L115】
  2. El caso de uso valida que el usuario esté activo, que TOTP esté habilitado, verifica el código contra el secreto guardado, registra el login exitoso y recupera los roles asociados (`roles` o fallback a `VISITOR`).【F:domain/usecase/src/main/java/co/turismo/usecase/authenticate/AuthenticateUseCase.java†L98-L119】
  3. `AuthenticateGateway.generateToken` emite el JWT y guarda la sesión en caché; el token se devuelve al cliente.【F:domain/usecase/src/main/java/co/turismo/usecase/authenticate/AuthenticateUseCase.java†L119-L121】【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L37-L56】
- **Ejemplo `curl`:**

```bash
curl -X POST https://turismo-back-production.up.railway.app/api/auth/login-code \
  -H "Content-Type: application/json" \
  -d '{ "email": "ana@example.com", "totpCode": 654321 }'
```

### 4) Refresco de sesión
- **Endpoint:** `POST /api/auth/refresh`
- **Flujo:** acepta el token actual por header `Authorization: Bearer` o en el body `{ "token": "..." }`, valida que exista en caché y dentro de la ventana de gracia y entrega un nuevo JWT rotando la sesión.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/handler/AuthenticateHandler.java†L118-L144】【F:infrastructure/driven-adapters/authenticate/src/main/java/co/turismo/authenticate/AuthenticateGateway.java†L73-L129】
- **Ejemplo `curl`:**

```bash
curl -X POST https://turismo-back-production.up.railway.app/api/auth/refresh \
  -H "Authorization: Bearer <token-actual>" \
  -H "Content-Type: application/json" \
  -d '{ "token": "<token-actual>" }'
```

## Notas operativas
- El filtro de seguridad permite libremente los endpoints de autenticación (`/api/auth/**`) y protege el resto, agregando los roles leídos del JWT a la autenticación de Spring Security.【F:infrastructure/entry-points/reactive-web/src/main/java/co/turismo/api/security/SecurityConfig.java†L137-L214】
- Los roles asignados en base de datos (`roles` y `user_roles`) controlan el acceso a rutas como creación de lugares o endpoints de administración; si no se asignan roles específicos, el fallback durante login es `VISITOR`.
