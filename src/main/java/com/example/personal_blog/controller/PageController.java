package com.example.personal_blog.controller;

import com.example.personal_blog.common.PublicEndpoint;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only client routes fall back to the SPA; missing API and asset paths stay 404. */
@Controller
public class PageController {
    @PublicEndpoint(ignoreToken = true)
    @GetMapping({"/", "/login", "/register", "/publish", "/article/{id:[0-9]+}"})
    public String index() {
        return "forward:/index.html";
    }
}
