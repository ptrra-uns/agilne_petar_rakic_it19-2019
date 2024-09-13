package apiGateway.authentication;

import dtos.UserDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration 
@EnableWebFluxSecurity
public class ApiGatewayAuthentication {
	@Bean
	public MapReactiveUserDetailsService userDetailsService(BCryptPasswordEncoder encoder) {
		List<UserDetails> users = new ArrayList<>();
		List<UserDto> usersFromDatabase;

		ResponseEntity<UserDto[]> response =
				new RestTemplate().getForEntity("http://localhost:8770/users/all", UserDto[].class);

		usersFromDatabase = Arrays.asList(response.getBody());

		for (UserDto ud : usersFromDatabase) {
			users.add(User.withUsername(ud.getEmail())
					.password(encoder.encode(ud.getPassword()))
					.roles(ud.getRole())
					.build());
		}


		return new MapReactiveUserDetailsService(users);
	}

	@Bean
	public BCryptPasswordEncoder encoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
		http.csrf().disable().authorizeExchange()
				.pathMatchers("/currency-exchange/**").permitAll()

				.pathMatchers("/crypto-exchange/**").permitAll()

				.pathMatchers("/currency-conversion/from/{from}/to/{to}/quantity/{quantity}").hasRole("USER")

				.pathMatchers("/crypto-conversion/**").hasRole("USER")

				.pathMatchers("/users/all").hasAnyRole("ADMIN", "OWNER")
				.pathMatchers("/users/{email}").permitAll()
				.pathMatchers("/users/create").hasAnyRole("ADMIN", "OWNER")
				.pathMatchers("/users/update/{email}").hasAnyRole("ADMIN", "OWNER")
				.pathMatchers("/users/delete/{email}").hasRole("OWNER")

				.pathMatchers("/bank-account/user/{email}").permitAll()
				.pathMatchers("/bank-account/create/{email}").hasRole("ADMIN")
				.pathMatchers("/bank-account/update/{email}").hasRole("ADMIN")
				.pathMatchers("/bank-account/update/{oldEmail}/for/{newEmail}").hasRole("ADMIN")
				.pathMatchers("/bank-account/update/user/{email}/subtract/{quantityS}from/{currS}/add/{quantityA}to/{currA}").permitAll()
				.pathMatchers("/bank-account/delete/{email}").hasRole("ADMIN")

				.pathMatchers("/crypto-wallet/user/{email}").permitAll()
				.pathMatchers("/crypto-wallet/create/{email}").hasRole("ADMIN")
				.pathMatchers("/crypto-wallet/update/{email}").hasRole("ADMIN")
				.pathMatchers("/crypto-wallet/update/{oldEmail}/for/{newEmail}").hasRole("ADMIN")
				.pathMatchers("/crypto-wallet/delete/{email}").hasRole("ADMIN")

				.pathMatchers("/transfer-service/**").hasRole("USER")
				.pathMatchers("/trade-service/**").hasRole("USER")
				.and().httpBasic().and()
				.addFilterAfter((exchange, chain) -> {
					return ReactiveSecurityContextHolder.getContext()
							.map(context -> context.getAuthentication())
							.flatMap(authentication -> {
								String role = authentication.getAuthorities().iterator().next().getAuthority();
								String email = authentication.getName();

								ServerWebExchange modifiedExchange = exchange.mutate()
										.request(builder -> builder.header("X-User-Role", role))
										.request(builder -> builder.header("X-User-Email", email))
										.build();
								return chain.filter(modifiedExchange);
							});
				}, SecurityWebFiltersOrder.AUTHORIZATION)
				.authorizeExchange();


		return http.build();
	}
}