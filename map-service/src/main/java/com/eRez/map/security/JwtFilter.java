package com.eRez.map.security;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Slf4j
@Component("jwtFilter")
public class JwtFilter extends OncePerRequestFilter {

    private final MongoTemplate usersMongoTemplate;

    public JwtFilter(@Qualifier("usersMongoTemplate") MongoTemplate usersMongoTemplate) {
        this.usersMongoTemplate = usersMongoTemplate;
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
        Document doc = usersMongoTemplate.findOne(
                new Query(Criteria.where("token").is(rawToken)),
                Document.class, "tokens");

        if (doc == null || !Boolean.TRUE.equals(doc.getBoolean("valid"))
                || doc.getDate("expiresAt").before(new Date())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                doc.getString("userId"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
