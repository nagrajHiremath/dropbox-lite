package com.dropbox.download_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * OBS-01: propagates the X-Request-Id the API Gateway's CorrelationIdWebFilter
 * generates/echoes, so this service's logs (via logging.pattern.level's
 * %X{requestId}) can be correlated back to a single caller request. Generates
 * one if absent (e.g. a direct call bypassing the gateway) rather than requiring it.
 *
 * Like CurrentUserHeaderFilter, this is skipped on the second async servlet
 * dispatch StreamingResponseBody triggers (OncePerRequestFilter's default
 * shouldNotFilterAsyncDispatch() = true) - the MDC value set here does not
 * persist into that dispatch. Unlike authentication, that's an acceptable
 * MVP gap for logging correlation, not a correctness issue.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String requestId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
