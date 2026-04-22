package com.rockiot.tag.controller;

import com.rockiot.tag.model.User;
import com.rockiot.tag.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public User login(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        String cid = params.get("cid");
        return authService.login(name, cid);
    }

    @PostMapping("/register")
    public User register(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        String cid = params.get("cid");
        return authService.register(name, cid);
    }
}