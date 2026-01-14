package order_system.pickup.partner.client;

import order_system.pickup.partner.dto.PartnerAOrderRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerAClient {

    private final RestClient partnerARestClient;

    public PartnerAClient(RestClient partnerARestClient) {
        this.partnerARestClient = partnerARestClient;
    }

    public void sendOrder(PartnerAOrderRequest req, boolean forceFail) {
        partnerARestClient.post()
                .uri("/mock/partner-a/orders")
                .header("Content-Type", "application/json")
                .headers(h -> {
                    if (forceFail) {
                        h.add("X-Mock-Fail", "true");
                    }
                })
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("PartnerA 전송 실패: HTTP " + response.getStatusCode());
                })
                .toBodilessEntity();
    }
}
