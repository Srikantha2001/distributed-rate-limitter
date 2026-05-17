package io.sriki.distributed_rate_limitter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "token.bucket")
@Getter
@Setter
public class TokenBucketConfigurationProperties {
    private int bucketCapacity=10;
    private double refillRatePerSecond=1.0;
}
