package io.sriki.distributed_rate_limitter;

import io.sriki.distributed_rate_limitter.config.SlidingWindowConfigurationProperties;
import io.sriki.distributed_rate_limitter.config.TokenBucketConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({TokenBucketConfigurationProperties.class, SlidingWindowConfigurationProperties.class})
public class DistributedRateLimitterApplication {

	public static void main(String[] args) {
		SpringApplication.run(DistributedRateLimitterApplication.class, args);
	}

}
