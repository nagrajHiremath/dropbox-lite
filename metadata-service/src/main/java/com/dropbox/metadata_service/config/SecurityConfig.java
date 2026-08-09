package com.dropbox.metadata_service.config;

import com.dropbox.metadata_service.security.CurrentUserHeaderFilter;
import com.dropbox.metadata_service.security.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/v1/public/**").permitAll()
				.requestMatchers("/api/v1/internal/public-shares/**").permitAll()
				.anyRequest().authenticated())
			.addFilterBefore(new RequestIdFilter(), UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(new CurrentUserHeaderFilter(), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
