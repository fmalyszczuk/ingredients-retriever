package com.malyszczuk.ingredients_retriever.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequireHttpsFilterTest {

    private final RequireHttpsFilter filter = new RequireHttpsFilter();

    @Test
    void aSecureRequest_goesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals(request, chain.getRequest(), "the chain was continued");
    }

    @Test
    void aPlainHttpRequest_isRefused_andNeverReachesTheApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("HTTPS required", response.getContentAsString());
        assertNull(chain.getRequest(), "the chain was not continued");
    }
}
