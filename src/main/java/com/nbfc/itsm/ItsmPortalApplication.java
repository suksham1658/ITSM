package com.nbfc.itsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Starts the portal in two ways from the same build:
 * <ul>
 *   <li><b>Embedded Tomcat</b> (local development): run {@link #main} from the IDE, or
 *       {@code java -jar target/itsm-portal.war}.</li>
 *   <li><b>External Tomcat 9</b> (server): copy {@code target/itsm-portal.war} to Tomcat's
 *       {@code webapps}; Tomcat calls {@link #configure} through {@link SpringBootServletInitializer}.
 *       Use Tomcat 9.x, not 10+ (this app uses {@code javax.servlet}).</li>
 * </ul>
 */
@SpringBootApplication
public class ItsmPortalApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ItsmPortalApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ItsmPortalApplication.class);
    }
}
