package com.eRez.map.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class UsersMongoConfig {

    @Value("${users.mongodb.uri}")
    private String usersMongoUri;

    @Bean(name = "usersMongoClient")
    public MongoClient usersMongoClient() {
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(usersMongoUri))
                .build());
    }

    @Bean(name = "usersMongoTemplate")
    public MongoTemplate usersMongoTemplate(@Qualifier("usersMongoClient") MongoClient usersMongoClient) {
        String database = new ConnectionString(usersMongoUri).getDatabase();
        return new MongoTemplate(usersMongoClient, database);
    }
}
