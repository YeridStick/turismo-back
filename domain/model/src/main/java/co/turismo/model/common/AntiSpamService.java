package co.turismo.model.common;

import reactor.core.publisher.Mono;

public interface AntiSpamService {
    Mono<Void> checkDeviceQuota(String deviceId); // error si excede cuota
}