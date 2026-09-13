package com.popman.arca.config;

import com.popman.arca.filter.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Autowired
  private UserDetailsService userDetailsService;

  @Autowired
  private JwtFilter jwtFilter;

  @Value("${app.cors.allowed-origins:*}")
  private List<String> allowedOrigins;

  @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
  private List<String> allowedMethods;

  @Value("${app.cors.allowed-headers:*}")
  private List<String> allowedHeaders;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/user/*/profile-picture").permitAll()
            .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/user/me").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/user/me").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/user/*/profile-picture")
            .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/user").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/user").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/user/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/user/*").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/user/*").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/posts/pending", "/api/v1/posts/pending/**")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/posts/*/approve").hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/schools/**", "/api/v1/departments/**", "/api/v1/subject/**")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/schools/**", "/api/v1/departments/**", "/api/v1/subject/**")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/schools/**", "/api/v1/departments/**", "/api/v1/subject/**")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/schools/**", "/api/v1/departments/**", "/api/v1/subject/**")
            .hasAuthority("ROLE_ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/v1/schools/**", "/api/v1/departments/**", "/api/v1/subject/**")
            .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers("/api/v1/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
            .requestMatchers("/v3/api-docs",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-ui/index.html")
            .permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, exception) ->
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Authentication required"))
            .accessDeniedHandler((request, response, exception) ->
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Access denied")))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // Use explicit origins configured in application.properties
    configuration.setAllowedOrigins(defaultIfEmpty(allowedOrigins, List.of("*")));
    configuration
        .setAllowedMethods(defaultIfEmpty(allowedMethods, List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")));
    configuration
        .setAllowedHeaders(defaultIfEmpty(allowedHeaders, List.of("Authorization", "Content-Type", "Cache-Control")));
    configuration.setAllowCredentials(true);
    configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private List<String> defaultIfEmpty(List<String> value, List<String> fallback) {
    return (value == null || value.isEmpty()) ? fallback : value;
  }

  private static void writeJsonError(HttpServletResponse response, int status, String error, String message)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setPasswordEncoder(passwordEncoder());
    authProvider.setUserDetailsService(userDetailsService);
    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }
}
