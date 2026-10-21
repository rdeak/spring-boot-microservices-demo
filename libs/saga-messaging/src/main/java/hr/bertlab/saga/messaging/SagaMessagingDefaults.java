package hr.bertlab.saga.messaging;

import java.io.IOException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Tracing defaults for every application that has this library on the classpath, added with the
 * lowest precedence so a service's application.yaml, the environment or the command line override
 * them. Registered in {@code META-INF/spring.factories}; the values are in
 * {@code saga-messaging-defaults.yaml}.
 */
public class SagaMessagingDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE = "saga-messaging-defaults";

    private static final String RESOURCE = "saga-messaging-defaults.yaml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            PropertySource<?> defaults = new YamlPropertySourceLoader()
                    .load(PROPERTY_SOURCE, new ClassPathResource(RESOURCE))
                    .get(0);
            environment.getPropertySources().addLast(defaults);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load " + RESOURCE, ex);
        }
    }
}
