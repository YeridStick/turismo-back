package co.turismo.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class UseCasesConfigTest {

    @Test
    void testUseCaseClassesAreDiscoveredByScan() {
        // Mismo criterio que tu UseCasesConfig:
        // basePackages = "co.turismo.usecase"
        // includeFilters REGEX "^.+UseCase$"
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(
                new RegexPatternTypeFilter(Pattern.compile("^.+UseCase$"))
        );

        Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                scanner.findCandidateComponents("co.turismo.usecase");

        // Aserción: deben existir clases que terminen en UseCase
        assertFalse(candidates.isEmpty(), "No se encontraron clases *UseCase* bajo co.turismo.usecase");
    }
}
