package order_system.pickup.partner.mock;

import jakarta.validation.Valid;
import order_system.pickup.partner.dto.PartnerAOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class PartnerMockController {

    private static final Logger log = LoggerFactory.getLogger(PartnerMockController.class);

    @PostMapping("/partner-a/orders")
    @ResponseStatus(HttpStatus.OK)
    public void receivePartnerAOrder(
            @RequestHeader(value = "X-Mock-Fail", required = false) String mockFail,
            @RequestBody @Valid PartnerAOrderRequest req
    ) {
        if ("true".equalsIgnoreCase(mockFail)) {
            log.warn("PartnerA mock forced failure. req={}", req);
            throw new PartnerMockFailureException("PartnerA forced failure");
        }

        log.info("PartnerA mock received order. req={}", req);
    }
}
