package com.dsa.flawless;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // DB 없이도 컨텍스트가 뜨도록 H2 사용 (src/test/resources/application-test.yaml)
class FlawlessApplicationTests {

    @Test
    void contextLoads() {
    }

}
