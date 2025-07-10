package com.planningapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
	
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**") // permite todas as rotas
				.allowedOrigins("http://localhost:4200") // frontend local
				.allowedMethods("*") // GET, POST, PUT, DELETE etc.
				.allowedHeaders("*")
				.allowCredentials(true);
	}
	

}
