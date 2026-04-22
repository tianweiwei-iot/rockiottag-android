package com.rockiot.tag.repository;

import com.rockiot.tag.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    User findByName(String name);
    User findByCid(String cid);
}