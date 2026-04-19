package co.turismo.authenticate.dto;

import java.util.Set;

public record SessionSnapshot(String email, Set<String> roles, String ip) {}
