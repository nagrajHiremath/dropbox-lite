package com.dropbox.download_service.config;

import com.dropbox.download_service.security.CurrentUserHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// Request-attribute backed (not session/cookie based) so it survives the
		// second async servlet dispatch StreamingResponseBody triggers for
		// downloads - the same HttpServletRequest instance is reused for that
		// dispatch, just on a possibly-different thread, and request attributes
		// are the one thing guaranteed to carry over. CurrentUserHeaderFilter
		// saves into this same repository after authenticating.
		SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

		http
			.csrf(csrf -> csrf.disable())
			.securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/api/v1/public/**").permitAll()
				.anyRequest().authenticated())
			.addFilterBefore(new CurrentUserHeaderFilter(securityContextRepository), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
