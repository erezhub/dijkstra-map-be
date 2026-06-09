package com.eRez.map;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.eRez.map", "com.eRez.common"})
public class MapApp {
    public static void main(String[] args) {
        SpringApplication.run(MapApp.class, args);
    }
}