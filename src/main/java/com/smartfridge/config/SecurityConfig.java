package com.smartfridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import javax.sql.DataSource;

@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsManager userDetailsManager(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);

        // 1. Fetch credentials directly using the username column
        manager.setUsersByUsernameQuery(
                "SELECT username, password, enabled FROM users_info WHERE username = ?"
        );

        // 2. JOIN tables to fetch roles because your roles table relies on user_id
        manager.setAuthoritiesByUsernameQuery(
                "SELECT u.username, r.role AS authority " +
                        "FROM roles r " +
                        "JOIN users_info u ON r.user_id = u.id " +
                        "WHERE u.username = ?"
        );

        return manager;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Roles are: ADMIN has access to all, CUSTOMER has access to only his data

        String admin = "ADMIN";
        String customer = "CUSTOMER";

        http.authorizeHttpRequests(configurer ->
                        configurer
                                .requestMatchers("/register/**").permitAll()
                                .requestMatchers("/access-denied").permitAll()
                                .requestMatchers("/").hasAnyRole(admin, customer)
                                .requestMatchers("/home/**").hasAnyRole(admin, customer)
                                .requestMatchers("/fridge/**").hasAnyRole(admin, customer)
                                .requestMatchers("/profile/**").hasAnyRole(admin, customer)
                                .requestMatchers("/systems/**").hasRole(admin)
                                .anyRequest().hasRole(admin))
                .formLogin(form->
                        form.
                                loginPage("/showMyLoginPage")
                                .loginProcessingUrl("/authenticateTheUser")
                                .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/showMyLoginPage?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(configurer ->
                        configurer.accessDeniedPage("/access-denied"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

}
