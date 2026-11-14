package com.popman.arca.config;

import com.popman.arca.filter.JwtFilter;
import com.popman.arca.exceptions.ApiErrors;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.net.URI;
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

  @Autowired
  private Environment environment;

  @Value("${app.cors.allowed-origins:}")
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
            .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
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
                ApiErrors.write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "Authentication required"))
            .accessDeniedHandler((request, response, exception) ->
                ApiErrors.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Access denied")))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    validateOrigins();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration
        .setAllowedMethods(defaultIfEmpty(allowedMethods, List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")));
    configuration
        .setAllowedHeaders(defaultIfEmpty(allowedHeaders, List.of("Authorization", "Content-Type", "Cache-Control", "X-Request-ID")));
    configuration.setAllowCredentials(true);
    configuration.validateAllowCredentials();
    configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Request-ID"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private List<String> defaultIfEmpty(List<String> value, List<String> fallback) {
    return (value == null || value.isEmpty()) ? fallback : value;
  }

  private void validateOrigins() {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      throw new IllegalArgumentException("Explicit CORS origins are required");
    }
    boolean development = environment.acceptsProfiles(Profiles.of("dev", "test"))
        && !environment.acceptsProfiles(Profiles.of("prod", "production"));
    for (String origin : allowedOrigins) {
      URI uri;
      try { uri = URI.create(origin); }
      catch (IllegalArgumentException | NullPointerException e) { throw new IllegalArgumentException("Invalid CORS origin"); }
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (host == null || origin.contains("*") || !("http".equals(scheme) || "https".equals(scheme))
          || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
          || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
          || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535) {
        throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without paths or wildcards");
      }
      boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
      if (!development && "http".equals(scheme) && !loopback) {
        throw new IllegalArgumentException("Production CORS origins require HTTPS");
      }
    }
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
