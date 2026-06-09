package com.parkomfy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebAdminController {

    @GetMapping({"/admin", "/admin/"})
    public String adminHome() {
        return "redirect:/admin/index.html";
    }
}
