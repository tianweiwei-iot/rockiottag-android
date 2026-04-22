package com.rockiot.tag.service;

import com.rockiot.tag.model.User;
import com.rockiot.tag.repository.UserRepository;
import com.rockiot.tag.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    public User login(String name, String cid) {
        User user = userRepository.findByName(name);
        if (user == null) {
            user = register(name, cid);
        } else {
            if (cid != null && !cid.equals(user.getCid())) {
                user.setCid(cid);
            }
        }
        String token = JwtUtil.generateToken(user.getId());
        user.setToken(token);
        userRepository.save(user);
        return user;
    }

    public User register(String name, String cid) {
        User existingUser = userRepository.findByName(name);
        if (existingUser != null) {
            return existingUser;
        }
        
        User user = new User();
        user.setName(name);
        user.setCid(cid);
        return userRepository.save(user);
    }

    public User getUserById(int userId) {
        return userRepository.findById(userId).orElse(null);
    }
}