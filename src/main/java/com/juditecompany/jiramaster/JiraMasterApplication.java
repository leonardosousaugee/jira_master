package com.juditecompany.jiramaster;

import com.juditecompany.jiramaster.config.JiraOAuthProperties;
import com.juditecompany.jiramaster.config.JiraProperties;
import com.juditecompany.jiramaster.config.TarifaProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JiraProperties.class, JiraOAuthProperties.class, TarifaProperties.class})
public class JiraMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraMasterApplication.class, args);
    }

    // ts do ledger e gravado em UTC: o Jira devolve tudo em UTC e misturar fusos num campo
    // sem timezone deixaria a linha ambigua.
    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
