package com.example.personal_blog;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingTests {
    @Autowired MockMvc mvc;

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/user/login").contentType("text/plain").content("任意内容"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
    }

    @Test
    void unacceptableResponseTypeReturns406() throws Exception {
        mvc.perform(get("/api/plaza/categories").accept("application/xml"))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void unmappedPostRemains404() throws Exception {
        mvc.perform(post("/api/definitely-missing").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void directErrorUsesTheResultEnvelopeAndDisablesCaching() throws Exception {
        mvc.perform(get("/error"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.timestamp").doesNotExist()).andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void dispatchedErrorsPreserveTheirStatusAndHideServletDetails() throws Exception {
        for (int code : new int[]{401, 403, 404, 410, 500}) {
            mvc.perform(get("/error").servletPath("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                            .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/private-path"))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.timestamp").doesNotExist()).andExpect(jsonPath("$.path").doesNotExist())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }
}
