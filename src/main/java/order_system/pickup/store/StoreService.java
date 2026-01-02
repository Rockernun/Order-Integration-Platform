package order_system.pickup.store;

import order_system.pickup.store.dto.StoreCreateRequest;
import order_system.pickup.store.dto.StoreResponse;
import org.springframework.stereotype.Service;

@Service
public class StoreService {

    private final StoreRepository storeRepository;

    public StoreService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

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
