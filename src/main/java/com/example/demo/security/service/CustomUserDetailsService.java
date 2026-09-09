package com.example.demo.security.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        initializeUserAssociations(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User loadUserEntityByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        initializeUserAssociations(user);
        return user;
    }

    private void initializeUserAssociations(User user) {
        Hibernate.initialize(user.getRoles());
        user.getRoles().forEach(role -> Hibernate.initialize(role.getPermissions()));
        Hibernate.initialize(user.getTenant());
        Hibernate.initialize(user.getDepartment());
    }
}