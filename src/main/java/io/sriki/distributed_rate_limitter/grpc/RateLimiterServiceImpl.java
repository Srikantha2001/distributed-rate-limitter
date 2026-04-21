package io.sriki.distributed_rate_limitter.grpc;

import io.grpc.stub.StreamObserver;
import io.sriki.ratelimiter.proto.RateLimitRequest;
import io.sriki.ratelimiter.proto.RateLimitResponse;
import io.sriki.ratelimiter.proto.RateLimiterServiceGrpc;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class RateLimiterServiceImpl extends RateLimiterServiceGrpc.RateLimiterServiceImplBase {

    @Override
    public void checkRateLimit(RateLimitRequest request, StreamObserver<RateLimitResponse> responseObserver) {
        RateLimitResponse response = RateLimitResponse.newBuilder()
                .setAllowed(true)
                .setRemaining(100)
                .setResetAfterMs(1000)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}