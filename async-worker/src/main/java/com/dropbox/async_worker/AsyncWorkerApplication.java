package com.dropbox.async_worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AsyncWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsyncWorkerApplication.class, args);
	}

}
