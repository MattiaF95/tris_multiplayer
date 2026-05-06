package it.gol8.secure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests
                        // 1. Risorse pubbliche
                        .requestMatchers("/", "/login", "/public-info", "/css/**", "/js/**", "/images/**").permitAll()
                        // // 2. Protezione specifica per ADMIN
                        // .requestMatchers("/admin-only", "/admin-only/**").hasRole("ADMIN")
                        // 3. Tutto il resto richiede login (USER o ADMIN)
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll())
                .logout((logout) -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling((exception) -> exception
                        .accessDeniedPage("/access-denied") // Mancava la chiusura della lambda qui
                ); // E qui la parentesi del metodo http

        return http.build();
    }

    // private final UserService userService;

    // // Costruttore per l'iniezione
    // public SecurityConfig(UserService userService) {
    //     this.userService = userService;
    // }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username("franco")
                .password(passwordEncoder().encode("guile2026")) // Password cifrata
                .roles("USER")
                .build();

        UserDetails mattia = User.builder()
                .username("mattia")
                .password(passwordEncoder().encode("mattia123"))
                .roles("USER")
                .build();

        UserDetails patrizio = User.builder()
                .username("patrizio")
                .password(passwordEncoder().encode("pat123"))
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user, mattia, patrizio, admin);
    }

    // Bean per la cifratura delle password
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


}
