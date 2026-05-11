package io.sriki.distributed_rate_limitter.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.sriki.distributed_rate_limitter.algorithm.RateLimiterAlgorithm;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmRequest;
import io.sriki.distributed_rate_limitter.model.RateLimiterAlgorithmResponse;
import io.sriki.ratelimiter.proto.CheckRateLimitRequest;
import io.sriki.ratelimiter.proto.CheckRateLimitResponse;
import io.sriki.ratelimiter.proto.RateLimiterServiceGrpc;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
public class RateLimiterServiceImpl
    extends RateLimiterServiceGrpc.RateLimiterServiceImplBase
{

    private final RateLimiterAlgorithm algorithm;
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile(
        "^[A-Za-z0-9]+$"
    );

    public RateLimiterServiceImpl(RateLimiterAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public void checkRateLimit(
        CheckRateLimitRequest request,
        StreamObserver<CheckRateLimitResponse> responseObserver
    ) {
        validate(request);
        String storageKey = request.getClientId() + ':' + request.getResource();
        int tokens = request.getTokens() >= 1 ? request.getTokens() : 1;
        RateLimiterAlgorithmRequest algorithmRequest =
            new RateLimiterAlgorithmRequest(storageKey, tokens);
        RateLimiterAlgorithmResponse algorithmResponse = algorithm.isAllowed(
            algorithmRequest
        );
        CheckRateLimitResponse response = CheckRateLimitResponse.newBuilder()
            .setAllowed(algorithmResponse.allowed())
            .setRemaining((long) algorithmResponse.remainingToken())
            .setRetryAfterMs(
                (long) (algorithmResponse.retryAfterNanos() / Math.pow(10, 6))
            )
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void validate(CheckRateLimitRequest request) {
        log.info("validating the incoming request : {}", request);
        if (request.getClientId().isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription(
                "Client Id should be present and non-empty"
            ).asRuntimeException();
        }
        if (!CLIENT_ID_PATTERN.matcher(request.getClientId()).matches()) {
            throw Status.INVALID_ARGUMENT.withDescription(
                "Client Id should be alphanumeric only"
            ).asRuntimeException();
        }
        if (request.getResource().isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription(
                "Resource should be present and non-empty"
            ).asRuntimeException();
        }
    }
}
