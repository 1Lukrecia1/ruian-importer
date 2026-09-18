package cz.trixi.ruian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RuianImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuianImporterApplication.class, args);
    }
}
