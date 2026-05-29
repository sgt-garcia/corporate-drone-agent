package ai.corporatedroneagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.model.chat=azure-openai",
        "spring.ai.azure.openai.endpoint="
})
class FirstRunSpringAiAutoConfigurationTests {

    @Test
    void contextLoadsWithoutAzureOpenAiEndpoint() {
    }
}
