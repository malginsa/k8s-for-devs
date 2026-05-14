package com.ms.intro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
// eureka configured with properties
//@EnableDiscoveryClient
public class SongsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SongsApplication.class, args);
    }
}
