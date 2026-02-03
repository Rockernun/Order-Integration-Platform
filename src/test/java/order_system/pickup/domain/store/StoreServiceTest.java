package order_system.pickup.domain.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import java.util.Optional;
import order_system.pickup.domain.store.dto.StoreCreateRequest;
import order_system.pickup.domain.store.dto.StoreResponse;
import order_system.pickup.domain.store.exception.StoreNotFoundException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;

import  org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    StoreRepository storeRepository;

    @InjectMocks
    StoreService storeService;

    @Test
    @DisplayName("createStore: Store 저장 후 결과를 반환")
    void createStore_success() {
        StoreCreateRequest request = new StoreCreateRequest("A 가게", "국민카드", "gm-001");
        Long savedId = 1L;

        StoreResponse response = new StoreResponse(
                savedId, "A 가게", "국민카드", "gm-001", "ACTIVE"
        );

        when(storeRepository.save(any(StoreCreateRequest.class))).thenReturn(savedId);
        when(storeRepository.findById(savedId)).thenReturn(Optional.of(response));

        StoreResponse result = storeService.createStore(request);

        Assertions.assertThat(result).isEqualTo(response);
        verify(storeRepository).save(request);
        verify(storeRepository).findById(savedId);
    }

    @Test
    @DisplayName("createStore: 저장 후 조회되지 않으면 예외를 던진다")
    void createStore_notFoundAfterSave() {
        StoreCreateRequest request = new StoreCreateRequest("A 가게", "국민카드", "gm-001");
        Long savedId = 1L;

        when(storeRepository.save(any(StoreCreateRequest.class))).thenReturn(savedId);
        when(storeRepository.findById(savedId)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> storeService.createStore(request))
                .isInstanceOf(StoreNotFoundException.class);

        verify(storeRepository).save(request);
        verify(storeRepository).findById(savedId);
    }

    @Test
    @DisplayName("getStore: 조회하는 Store가 존재하면 해당 StoreResponse를 가져온다.")
    void getStore_success() {
        long storeId = 1L;
        StoreResponse response = new StoreResponse(
                storeId, "A 가게", "국민카드", "gm-001", "ACTIVE"
        );

        when(storeRepository.findById(storeId)).thenReturn(Optional.of(response));

        StoreResponse result = storeService.getStore(storeId);
        Assertions.assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("getStore: 조회하는 Store가 존재하지 않으면 예외를 던진다")
    void getStore_notFound() {
        long storeId = Integer.MAX_VALUE;
        when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> storeService.getStore(storeId))
                .isInstanceOf(StoreNotFoundException.class);

        verify(storeRepository).findById(storeId);
    }
}