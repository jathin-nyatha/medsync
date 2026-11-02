package com.medsync.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.medsync.auth.entity.User;
import com.medsync.auth.repository.UserRepository;

@Service
public class MedSyncUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MedSyncUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        return new MedSyncUserDetails(user);
    }
}