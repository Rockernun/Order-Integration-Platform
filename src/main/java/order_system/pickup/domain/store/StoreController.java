package order_system.pickup.domain.store;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import order_system.pickup.domain.store.dto.StoreCreateRequest;
import order_system.pickup.domain.store.dto.StoreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse createStore(@RequestBody @Valid StoreCreateRequest req) {
        return storeService.createStore(req);
    }

    @GetMapping("/{storeId}")
    public StoreResponse getStore(@PathVariable Long storeId) {
        return storeService.getStore(storeId);
    }
}
