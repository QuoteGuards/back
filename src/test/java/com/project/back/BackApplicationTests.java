package com.project.back;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "gemini.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "MAIL_USERNAME=noreply@quoteguard.com",
        "MAIL_PASSWORD=test",
        "spring.mail.test-connection=false",
        "mail.from-address=noreply@quoteguard.com",
        "app.frontend-url=http://localhost:5173"
})
class BackApplicationTests {

    @Test
    void contextLoads() {
    }

}
