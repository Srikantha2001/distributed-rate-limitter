package io.sriki.distributed_rate_limitter.grpc;

import io.grpc.stub.StreamObserver;
import io.sriki.ratelimiter.proto.CheckRateLimitRequest;
import io.sriki.ratelimiter.proto.CheckRateLimitResponse;
import io.sriki.ratelimiter.proto.RateLimiterServiceGrpc;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class RateLimiterServiceImpl
    extends RateLimiterServiceGrpc.RateLimiterServiceImplBase
{

    @Override
    public void checkRateLimit(
        CheckRateLimitRequest request,
        StreamObserver<CheckRateLimitResponse> responseObserver
    ) {
        CheckRateLimitResponse response = CheckRateLimitResponse.newBuilder()
            .setAllowed(true)
            .setRemaining(100)
            .setRetryAfterMs(0)
            .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
