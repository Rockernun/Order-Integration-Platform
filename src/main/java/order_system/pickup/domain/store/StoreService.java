package order_system.pickup.domain.store;

import lombok.RequiredArgsConstructor;
import order_system.pickup.domain.store.dto.StoreCreateRequest;
import order_system.pickup.domain.store.dto.StoreResponse;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreResponse createStore(StoreCreateRequest req) {
        Long storeId = storeRepository.save(req);
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
    }

    public StoreResponse getStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
    }
}
