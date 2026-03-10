package org.thingai.app.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // For development, allow common local development origins
                // For production, configure specific allowed origins based on environment
                String allowedOrigins = System.getProperty("cors.allowed.origins",
                    "http://localhost:4200,http://localhost:8080,http://127.0.0.1:4200,http://127.0.0.1:8080");
                
                String[] origins = allowedOrigins.split(",");
                
                registry.addMapping("/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
