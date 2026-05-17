package io.sriki.distributed_rate_limitter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.ratelimiter.proto.CheckRateLimitRequest;
import io.sriki.ratelimiter.proto.CheckRateLimitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceImplTest {

    @Mock
    private RateLimiterAlgorithm algorithm;

    @Mock
    private StreamObserver<CheckRateLimitResponse> responseObserver;

    @InjectMocks
    private RateLimiterServiceImpl service;

    private CheckRateLimitRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CheckRateLimitRequest.newBuilder()
            .setClientId("client123")
            .setResource("/api/orders")
            .setTokens(2)
            .build();
    }

    @Test
    void checkRateLimit_allowedResponse_writesResponseAndCompletes() {
        when(algorithm.isAllowed(any())).thenReturn(
            new RateLimiterAlgorithmResponse(true, 42L, 0L)
        );

        service.checkRateLimit(validRequest, responseObserver);

        ArgumentCaptor<CheckRateLimitResponse> captor = ArgumentCaptor.forClass(
            CheckRateLimitResponse.class
        );
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        CheckRateLimitResponse response = captor.getValue();
        assertThat(response.getAllowed()).isTrue();
        assertThat(response.getRemaining()).isEqualTo(42L);
        assertThat(response.getRetryAfterMs()).isEqualTo(0L);
    }

    @Test
    void checkRateLimit_deniedResponse_propagatesRetryAfter() {
        when(algorithm.isAllowed(any())).thenReturn(
            new RateLimiterAlgorithmResponse(false, 0L, 1500L)
        );

        service.checkRateLimit(validRequest, responseObserver);

        ArgumentCaptor<CheckRateLimitResponse> captor = ArgumentCaptor.forClass(
            CheckRateLimitResponse.class
        );
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        CheckRateLimitResponse response = captor.getValue();
        assertThat(response.getAllowed()).isFalse();
        assertThat(response.getRemaining()).isEqualTo(0L);
        assertThat(response.getRetryAfterMs()).isEqualTo(1500L);
    }

    @Test
    void checkRateLimit_buildsStorageKeyAsClientIdColonResource() {
        when(algorithm.isAllowed(any())).thenReturn(
            new RateLimiterAlgorithmResponse(true, 10L, 0L)
        );

        service.checkRateLimit(validRequest, responseObserver);

        ArgumentCaptor<RateLimiterAlgorithmRequest> captor =
            ArgumentCaptor.forClass(RateLimiterAlgorithmRequest.class);
        verify(algorithm).isAllowed(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("client123:/api/orders");
    }

    @Test
    void checkRateLimit_explicitTokens_passedThrough() {
        when(algorithm.isAllowed(any())).thenReturn(
            new RateLimiterAlgorithmResponse(true, 10L, 0L)
        );
        CheckRateLimitRequest request = CheckRateLimitRequest.newBuilder()
            .setClientId("abc")
            .setResource("res")
            .setTokens(5)
            .build();

        service.checkRateLimit(request, responseObserver);

        ArgumentCaptor<RateLimiterAlgorithmRequest> captor =
            ArgumentCaptor.forClass(RateLimiterAlgorithmRequest.class);
        verify(algorithm).isAllowed(captor.capture());
        assertThat(captor.getValue().tokensRequired()).isEqualTo(5);
    }

    @Test
    void checkRateLimit_zeroTokens_defaultsToOne() {
        when(algorithm.isAllowed(any())).thenReturn(
            new RateLimiterAlgorithmResponse(true, 10L, 0L)
        );
        CheckRateLimitRequest request = CheckRateLimitRequest.newBuilder()
            .setClientId("abc")
            .setResource("res")
            .build();

        service.checkRateLimit(request, responseObserver);

        ArgumentCaptor<RateLimiterAlgorithmRequest> captor =
            ArgumentCaptor.forClass(RateLimiterAlgorithmRequest.class);
        verify(algorithm).isAllowed(captor.capture());
        assertThat(captor.getValue().tokensRequired()).isEqualTo(1);
    }

    @Test
    void checkRateLimit_emptyClientId_throwsInvalidArgument() {
        CheckRateLimitRequest request = CheckRateLimitRequest.newBuilder()
            .setClientId("")
            .setResource("res")
            .build();

        assertThatThrownBy(() ->
            service.checkRateLimit(request, responseObserver)
        )
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex ->
                assertThat(
                    ((StatusRuntimeException) ex).getStatus().getCode()
                ).isEqualTo(Status.Code.INVALID_ARGUMENT)
            );

        verifyNoInteractions(algorithm);
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void checkRateLimit_nonAlphanumericClientId_throwsInvalidArgument() {
        CheckRateLimitRequest request = CheckRateLimitRequest.newBuilder()
            .setClientId("client-123")
            .setResource("res")
            .build();

        assertThatThrownBy(() ->
            service.checkRateLimit(request, responseObserver)
        )
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex ->
                assertThat(
                    ((StatusRuntimeException) ex).getStatus().getCode()
                ).isEqualTo(Status.Code.INVALID_ARGUMENT)
            );

        verifyNoInteractions(algorithm);
    }

    @Test
    void checkRateLimit_emptyResource_throwsInvalidArgument() {
        CheckRateLimitRequest request = CheckRateLimitRequest.newBuilder()
            .setClientId("abc")
            .setResource("")
            .build();

        assertThatThrownBy(() ->
            service.checkRateLimit(request, responseObserver)
        )
            .isInstanceOf(StatusRuntimeException.class)
            .satisfies(ex ->
                assertThat(
                    ((StatusRuntimeException) ex).getStatus().getCode()
                ).isEqualTo(Status.Code.INVALID_ARGUMENT)
            );

        verifyNoInteractions(algorithm);
    }
}
