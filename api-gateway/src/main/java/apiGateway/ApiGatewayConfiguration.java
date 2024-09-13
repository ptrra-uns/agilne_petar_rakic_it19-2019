package apiGateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfiguration {

	@Bean
	public RouteLocator gateway(RouteLocatorBuilder builder) {
		return	builder.routes().route(p -> p.path("/currency-exchange/**").uri("lb://currency-exchange"))
				.route(p -> p.path("/currency-conversion/**").uri("lb://currency-conversion"))
				.route(p -> p.path("/users/**").uri("lb://users"))
				.route(p -> p.path("/crypto-wallet/**").uri("lb://crypto-wallet"))
				.route(p -> p.path("/crypto-conversion/**").uri("lb://crypto-conversion"))
				.route(p -> p.path("/bank-account/**").uri("lb://bank-account"))
				.route(p -> p.path("/transfer-service/**").uri("lb://transfer-service"))
				.route(p -> p.path("/trade-service/**").uri("lb://trade-service"))
				.route(p -> p.path("/crypto-exchange/**").uri("lb://crypto-exchange")).build();
	}
}
