package com.eRez.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
@Component("jwtFilter")
public class JwtFilter extends OncePerRequestFilter {

    private final MongoTemplate tokenValidationMongoTemplate;

    public JwtFilter(@Qualifier("tokenValidationMongoTemplate") MongoTemplate tokenValidationMongoTemplate) {
        this.tokenValidationMongoTemplate = tokenValidationMongoTemplate;
    }

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
        Document tokenDoc = tokenValidationMongoTemplate.findOne(
                new Query(Criteria.where("token").is(rawToken)),
                Document.class, "tokens");

        if (tokenDoc == null || !Boolean.TRUE.equals(tokenDoc.getBoolean("valid"))
                || tokenDoc.getDate("expiresAt").before(new Date())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String userId = tokenDoc.getString("userId");
        Document userDoc = tokenValidationMongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                Document.class, "users");

        if (userDoc == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String email = userDoc.getString("email");
        String identifier = email != null ? email : userDoc.getString("username");
        String role = userDoc.getString("role");

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var userDetails = User.withUsername(identifier).password("").authorities(authorities).build();
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
