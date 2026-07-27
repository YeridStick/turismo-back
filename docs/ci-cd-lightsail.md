# CI/CD de turismo-back

El workflow `.github/workflows/deploy-ecr.yml` ejecuta pruebas, construye una
imagen Docker, la publica en ECR con una etiqueta inmutable `sha-<commit>` y
despliega el contenedor en Lightsail. El despliegue valida
`/actuator/health` y restaura el contenedor anterior si la nueva versión no
queda saludable.

## GitHub Environment

Crear un Environment llamado `test`.

Variables:

- `AWS_REGION`: `us-east-1`
- `AWS_ROLE_ARN`: output `github_actions_role_arn` de Terraform
- `ECR_REPOSITORY`: `turismo-back`
- `LIGHTSAIL_HOST`: output de la IP estática de Lightsail
- `LIGHTSAIL_USER`: usuario SSH de la imagen, normalmente `ec2-user`

Secrets:

- `LIGHTSAIL_SSH_PRIVATE_KEY`: clave privada correspondiente a la clave pública
  autorizada en Lightsail.
- `LIGHTSAIL_SSH_KNOWN_HOSTS`: línea de `known_hosts` verificada para la
  instancia. No se desactiva la comprobación de identidad SSH.
- `TURISMO_ENV_FILE`: archivo dotenv completo usado al iniciar Spring Boot.

No configurar `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY`. GitHub obtiene
credenciales temporales mediante OIDC.

## Variables mínimas de la aplicación

`TURISMO_ENV_FILE` debe incluir, como mínimo, las variables obligatorias que
consume `application.yaml`:

```dotenv
DB_HOST=endpoint-privado-rds
DB_PORT=5432
DB_NAME=turismo
DB_SCHEMA=public
DB_USER=turismo_app
DB_PASSWORD=valor-secreto
JWT_SECRET=valor-secreto
LOCATIONIQ_KEY=valor-secreto
APP_PUBLIC_URL=http://IP_O_DOMINIO
APP_FRONTEND_URL=https://URL_FRONTEND
BREVO_API_KEY=valor-secreto
BREVO_MCP_API_KEY=valor-secreto
BREVO_SENDER_EMAIL=correo-verificado
BREVO_SENDER_NAME=Turismo
WOMPI_ENVIRONMENT=sandbox
WOMPI_BASE_URL=https://sandbox.wompi.co/v1
WOMPI_PUBLIC_KEY=valor-secreto
WOMPI_PRIVATE_KEY=valor-secreto
WOMPI_INTEGRITY_SECRET=valor-secreto
WOMPI_EVENTS_SECRET=valor-secreto
```

Si una integración todavía no está disponible, debe deshabilitarse mediante
su configuración soportada por la aplicación; no se deben inventar
credenciales.

## Primer despliegue

Antes de integrar el workflow a `master`:

1. Confirmar que Terraform terminó sin cambios pendientes.
2. Crear el usuario de aplicación en RDS y habilitar PostGIS.
3. Confirmar conectividad privada entre Lightsail y RDS.
4. Configurar las variables y secretos del Environment `test`.
5. Ejecutar manualmente el workflow desde la rama de revisión.

El backend se publica temporalmente en el puerto HTTP `80`. HTTPS y dominio
deben agregarse posteriormente mediante un proxy inverso y certificado TLS.
