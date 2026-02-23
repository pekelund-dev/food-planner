package com.foodplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodPlannerApplication.class, args);
    }
}
