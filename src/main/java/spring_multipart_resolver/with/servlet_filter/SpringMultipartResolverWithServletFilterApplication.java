package spring_multipart_resolver.with.servlet_filter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;

/**
 * Created by taesu on 2018-08-27.
 */
@SpringBootApplication
@EnableAutoConfiguration(exclude = {MultipartAutoConfiguration.class})
public class SpringMultipartResolverWithServletFilterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringMultipartResolverWithServletFilterApplication.class);
    }
}
