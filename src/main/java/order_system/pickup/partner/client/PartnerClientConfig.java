package order_system.pickup.partner.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PartnerClientConfig {

    @Bean
    public RestClient partnerARestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:8080")
                .build();
    }
}
