package org.thingai.app.config;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

import java.net.ServerSocket;

@Configuration
public class ServerPortConfig implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        if (isPortAvailable(80)) {
            factory.setPort(80);
            return;
        }

        int startPort = 12345;
        int endPort = 12400;
        for (int port = startPort; port <= endPort; port++) {
            if (isPortAvailable(port)) {
                factory.setPort(port);
                return;
            }
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            ignored.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
