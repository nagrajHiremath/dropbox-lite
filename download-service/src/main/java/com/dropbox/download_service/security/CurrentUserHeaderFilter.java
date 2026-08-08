package com.dropbox.download_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Trusts the {@code X-User-Id} header set by the API Gateway after it has already
 * validated the caller's JWT (see GWT-01). Download Service sits behind the gateway
 * and does not re-validate JWTs itself. Mirrors metadata-service/upload-service's
 * filter of the same name so all services derive identity the same way.
 *
 * Unlike the other services' copy, this one also explicitly saves the resulting
 * Authentication into a SecurityContextRepository. Download endpoints return
 * StreamingResponseBody, which Spring MVC fulfills via a second, async servlet
 * dispatch that re-runs the whole security filter chain on a (possibly different)
 * thread. As an OncePerRequestFilter, this filter is - correctly - skipped on that
 * second dispatch, and SecurityContextHolderFilter only ever loads from the
 * repository, it never auto-saves. Without an explicit save here, the async
 * dispatch would see no Authentication, fall back to anonymous, and get rejected
 * by AuthorizationFilter after the response was already committed.
 */
public class CurrentUserHeaderFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final SecurityContextRepository securityContextRepository;

    public CurrentUserHeaderFilter(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(USER_ID_HEADER);

        if (userIdHeader != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID userId = UUID.fromString(userIdHeader);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContext context = SecurityContextHolder.getContext();
                context.setAuthentication(authentication);
                securityContextRepository.saveContext(context, request, response);
            } catch (IllegalArgumentException ignored) {
                // malformed header: leave unauthenticated, downstream authorization will reject
            }
        }

        filterChain.doFilter(request, response);
    }
}
