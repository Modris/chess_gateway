package com.modris.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity // WebFlux security due to Spring Cloud Gateway
public class SecurityConfig {

	/*
	 Key points:
	  1) Frontend assets are permitAll.
	  2) If not authenticated we don't redirect with 302 to Login page but return 401 
	  Unauthorized. The SPA page can then Login when it wants.
	  3) Logout Returns 202 Accepted Response instead of 302 redirect to Keycloak logout link.
	  The reason being that frontend will be unable to follow the call because of CORS 
	  issues. With 202 status code and Keycloak logout link in Location header the frontend will simply call 
	  the Location logout link.
	 4) We Serve CSRF Tokens in a Cookie.
	 5) We have to subscribe to the CSRF token with csrfWebFilter. This is a known issue in github.
	 */
	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
	        ReactiveClientRegistrationRepository clientRegistrationRepository) {
	    return http
	            .authorizeExchange(exchange -> exchange
	                    .pathMatchers("/", "/*.css", "/*.js", "/favicon.ico", "/assets/**", "/*.webp", 
	                    		"/assets/*", "/websocket", "/app/websocket", "/topic/*",
	                    		"/history","/history/page/*", "/game","/game/*","/get/game/*").permitAll()
	                   .anyExchange().authenticated())
	            .exceptionHandling(exceptionHandling ->
	                    exceptionHandling.authenticationEntryPoint(
	                            new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
	            .oauth2Login(Customizer.withDefaults())
	            .logout(logout -> logout.logoutSuccessHandler(new VueLogoutSuccessHandler(clientRegistrationRepository)))
	            .csrf(csrf -> csrf
	                    .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
	                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
	            .build();
	}

	
	static final class SpaCsrfTokenRequestHandler extends 
						ServerCsrfTokenRequestAttributeHandler {
		
		private final ServerCsrfTokenRequestAttributeHandler delegate = new XorServerCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(ServerWebExchange exchange, Mono<CsrfToken> csrfToken) {
			
			 //* Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection of the CsrfToken when it is rendered in the response body.
			 
			this.delegate.handle(exchange, csrfToken);
		}

		@Override
		public Mono<String> resolveCsrfTokenValue(ServerWebExchange exchange, CsrfToken csrfToken) {
			final var hasHeader = exchange.getRequest().getHeaders().get(csrfToken.getHeaderName()).stream().filter(StringUtils::hasText).count() > 0;
			return hasHeader ? super.resolveCsrfTokenValue(exchange, csrfToken) : this.delegate.resolveCsrfTokenValue(exchange, csrfToken);
		}
	}
	
	static class VueLogoutSuccessHandler implements ServerLogoutSuccessHandler {
	    private final OidcClientInitiatedServerLogoutSuccessHandler delegate;

	    public VueLogoutSuccessHandler(ReactiveClientRegistrationRepository clientRegistrationRepository) {
	        this.delegate = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
	        this.delegate.setPostLogoutRedirectUri("{baseUrl}");
	    }

	    @Override
	    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
	        return delegate.onLogoutSuccess(exchange, authentication).then(Mono.fromRunnable(() -> {
	            exchange.getExchange().getResponse().setStatusCode(HttpStatus.ACCEPTED);
	        }));
	    }
	}

	@Bean
	WebFilter csrfWebFilter() {
		return (exchange, chain) -> {
			exchange.getResponse().beforeCommit(() -> Mono.defer(() -> {
				Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());
				return csrfToken != null ? csrfToken.then() : Mono.empty();
			}));
			return chain.filter(exchange);
		};
	}
}