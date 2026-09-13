package com.popman.arca;

import com.popman.arca.filter.JwtFilter;
import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.JWTService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityJwtFilterTest {

    @Test
    void malformedJwtReturnsStableJsonUnauthorized() throws Exception {
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.extractEmail("bad-token")).thenThrow(new MalformedJwtException("bad token"));
        JwtFilter filter = filter(jwtService, mock(ApplicationContext.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"error\":\"unauthorized\""));
    }

    @Test
    void bannedJwtUserKeepsExplicitJsonForbidden() throws Exception {
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.extractEmail("valid-token")).thenReturn("banned@example.com");
        BannedEmailService bannedEmailService = mock(BannedEmailService.class);
        when(bannedEmailService.isBannedV1("banned@example.com")).thenReturn(true);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(BannedEmailService.class)).thenReturn(bannedEmailService);
        JwtFilter filter = filter(jwtService, context);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Access blocked: email is banned"));
    }

    private JwtFilter filter(JWTService jwtService, ApplicationContext context) {
        JwtFilter filter = new JwtFilter();
        ReflectionTestUtils.setField(filter, "jwtService", jwtService);
        ReflectionTestUtils.setField(filter, "context", context);
        return filter;
    }
}
