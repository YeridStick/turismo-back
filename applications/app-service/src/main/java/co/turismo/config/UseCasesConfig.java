package co.turismo.config;

import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import co.turismo.usecase.place.filterFactory.PlaceSearchFactory;
import co.turismo.usecase.place.filterStrategy.AllPlacesStrategy;
import co.turismo.usecase.place.filterStrategy.NearbySearchStrategy;
import co.turismo.usecase.place.filterStrategy.TextSearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.List;

@Configuration
@ComponentScan(basePackages = "co.turismo.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false)
public class UseCasesConfig {
        // 1. Definimos las estrategias individualmente
        @Bean
        public AllPlacesStrategy allPlacesStrategy(PlaceRepository repository) {
                return new AllPlacesStrategy(repository);
        }

        @Bean
        public NearbySearchStrategy nearbySearchStrategy(PlaceRepository repository) {
                return new NearbySearchStrategy(repository);
        }

        @Bean
        public TextSearchStrategy textSearchStrategy(PlaceRepository repository) {
                return new TextSearchStrategy(repository);
        }

        // 2. El Factory recibe la lista de los beans definidos arriba automáticamente
        @Bean
        public PlaceSearchFactory placeSearchFactory(List<PlaceSearchStrategy> strategyList) {
                return new PlaceSearchFactory(strategyList);
        }
}
