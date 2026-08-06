package com.juditecompany.jiramaster;

import com.juditecompany.jiramaster.config.JiraProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JiraProperties.class)
public class JiraMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraMasterApplication.class, args);
    }
}
