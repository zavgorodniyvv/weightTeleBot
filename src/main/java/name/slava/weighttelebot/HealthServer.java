package name.slava.weighttelebot;

import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Минимальный HTTP-сервер на встроенном в JDK HttpServer.
 * Нужен только для того, чтобы Render видел открытый порт (вместо Tomcat из spring-boot-starter-web).
 */
@Component
public class HealthServer {
    private static final Logger logger = LoggerFactory.getLogger(HealthServer.class);
    private static final byte[] RESPONSE = "OK".getBytes(StandardCharsets.UTF_8);

    private final int port;
    private HttpServer server;

    public HealthServer(@Value("${server.port:10000}") int port) {
        this.port = port;
    }

    @PostConstruct
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            try (exchange; OutputStream body = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(200, RESPONSE.length);
                body.write(RESPONSE);
            }
        });
        server.start();
        logger.info("Health server listening on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
