package cz.trixi.ruian;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostGIS database for integration tests. The schema is created from the same {@code db/schema.sql}
 * as in production (see {@code spring.sql.init} in application-test.yml).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgisContainerConfig {

    // the official postgis/postgis images have no arm64 build, this one is the arm64 rebuild
    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("imresamu/postgis:17-3.5-alpine")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgisContainer() {
        return new PostgreSQLContainer<>(POSTGIS_IMAGE);
    }
}
