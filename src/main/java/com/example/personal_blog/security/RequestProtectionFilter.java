package com.example.personal_blog.security;

import com.example.personal_blog.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class RequestProtectionFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ObjectMapper json;

    public RequestProtectionFilter(SecurityProperties properties, ObjectMapper json) {
        maxBytes = properties.getMaxRequestBytes();
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
                + "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        // Check actual bytes too: Content-Length can be absent (e.g. chunked requests).
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getWriter(), new Result<>(413, "请求内容过大，请缩短后重试", null));
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request body"); }
            };
        }

        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
