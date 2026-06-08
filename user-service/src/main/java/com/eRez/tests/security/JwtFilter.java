package com.eRez.tests.security;

import com.eRez.tests.database.document.TokenDocument;
import com.eRez.tests.database.document.UserDocument;
import com.eRez.tests.database.repository.TokenRepository;
import com.eRez.tests.database.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("jwtFilter")
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(7);
        Optional<TokenDocument> tokenDoc = tokenRepository.findByToken(rawToken);

        if (tokenDoc.isEmpty() || !tokenDoc.get().isValid()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Optional<UserDocument> userOpt = userRepository.findById(tokenDoc.get().getUserId());
        if (userOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UserDocument user = userOpt.get();
        String principal = user.getEmail() != null ? user.getEmail() : user.getUsername();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(principal)
                .password("")
                .authorities(authorities)
                .build();
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
