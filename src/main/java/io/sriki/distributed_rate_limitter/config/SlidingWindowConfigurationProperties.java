package io.sriki.distributed_rate_limitter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sliding.window")
@Getter
@Setter
public class SlidingWindowConfigurationProperties {
    private int windowSizeInSeconds = 60;
    private int maxRequests = 100;
}
