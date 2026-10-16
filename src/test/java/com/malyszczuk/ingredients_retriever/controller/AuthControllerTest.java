package com.malyszczuk.ingredients_retriever.controller;

import com.malyszczuk.ingredients_retriever.auth.AccountService;
import com.malyszczuk.ingredients_retriever.auth.AuthThrottle;
import com.malyszczuk.ingredients_retriever.auth.EmailAlreadyRegisteredException;
import com.malyszczuk.ingredients_retriever.auth.InvalidPasswordException;
import com.malyszczuk.ingredients_retriever.auth.LoginService;
import com.malyszczuk.ingredients_retriever.auth.UserSessions;
import com.malyszczuk.ingredients_retriever.domain.AppUser;
import com.malyszczuk.ingredients_retriever.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller logic only: authentication is covered by SecurityIntegrationTest, which runs the real filter chain.
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private AuthThrottle authThrottle;

    @MockitoBean
    private UserSessions userSessions;

    private String register(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void register_createsTheAccount_andNeverReturnsAnythingAboutThePassword() throws Exception {
        when(accountService.register("me@example.com", "a decent passphrase"))
                .thenReturn(AppUser.builder().id(7L).email("me@example.com").passwordHash("{bcrypt}$2a$12$secret-hash").build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\", \"password\": \"a decent passphrase\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("secret-hash"))))
                .andExpect(content().string(not(containsString("passphrase"))));
    }

    @Test
    void register_trimsSurroundingSpacesFromTheEmail() throws Exception {
        when(accountService.register("me@example.com", "a decent passphrase"))
                .thenReturn(AppUser.builder().id(1L).email("me@example.com").build());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"  me@example.com \", \"password\": \"a decent passphrase\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void register_answers409_whenTheEmailIsAlreadyTaken() throws Exception {
        when(accountService.register("me@example.com", "a decent passphrase")).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\", \"password\": \"a decent passphrase\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void register_answers400WithTheReason_whenThePasswordIsNotAcceptable() throws Exception {
        when(accountService.register("me@example.com", "password123")).thenThrow(new InvalidPasswordException("That password is too common or too easy to guess"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"me@example.com\", \"password\": \"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("too common")))
                .andExpect(content().string(not(containsString("password123"))));
    }

    @Test
    void register_answers400_forMalformedInput_withoutCallingTheService() throws Exception {
        String body = register("not-an-email", "a decent passphrase");
        assertFalse(body.contains("a decent passphrase"));

        for (String json : new String[]{
                "{\"email\": \"not-an-email\", \"password\": \"a decent passphrase\"}",
                "{\"email\": \"\", \"password\": \"a decent passphrase\"}",
                "{\"password\": \"a decent passphrase\"}",
                "{\"email\": \"me@example.com\"}",
                "{\"email\": \"me@example.com\", \"password\": \"\"}",
                "{\"email\": \"" + "a".repeat(250) + "@example.com\", \"password\": \"a decent passphrase\"}",
                "{\"email\": \"me@example.com\", \"password\": \"" + "x".repeat(2000) + "\"}",
                "{}"}) {
            mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(content().string(not(containsString("a decent passphrase"))));
        }
        verifyNoInteractions(accountService);
    }

    @Test
    void register_namesTheFieldsThatWereWrong() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nope\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("email:")))
                .andExpect(content().string(containsString("password:")));
    }

    @Test
    void theRequestObject_masksThePasswordWhenPrinted() {
        String printed = new RegisterRequest("me@example.com", "super secret passphrase").toString();

        assertFalse(printed.contains("super secret passphrase"));
    }
}
