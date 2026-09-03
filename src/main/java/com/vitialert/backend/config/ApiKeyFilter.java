package com.vitialert.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitialert.backend.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Filtro de clave simple. Se mantiene deliberadamente minimo: el ESP32 no puede negociar
 * OAuth y el prototipo debe seguir siendo usable en local con la seguridad desactivada.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final String headerName;
    private final Predicate<String> validator;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(String headerName, Predicate<String> validator, ObjectMapper objectMapper) {
        this.headerName = headerName;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(headerName);
        if (provided != null && !provided.isBlank() && validator.test(provided)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Peticion rechazada por clave invalida o ausente ({}) en {}", headerName, request.getRequestURI());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                "Falta la cabecera " + headerName + " o su valor no es valido.",
                request.getRequestURI()));
    }
}
