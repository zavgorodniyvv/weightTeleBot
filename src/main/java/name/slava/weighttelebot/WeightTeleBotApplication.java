package name.slava.weighttelebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class WeightTeleBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeightTeleBotApplication.class, args);
    }

}
