package co.turismo.model.sitemedia.gateways;

import co.turismo.model.sitemedia.SiteMedia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SiteMediaRepository {
    Mono<SiteMedia> save(SiteMedia media);
    Flux<SiteMedia> findBySiteId(Long siteId);
    Mono<SiteMedia> findByIdForSite(Long id, Long siteId);
    Mono<Boolean> deleteByIdForSite(Long id, Long siteId);
}
