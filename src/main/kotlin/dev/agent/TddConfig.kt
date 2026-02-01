package dev.agent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@SpringBootApplication
class TddApplication

@Configuration
class TddConfig {
    @Bean
    fun llmAdapter(): LlmAdapter {
        return OpenCodeAdapter(model = null, timeout = Duration.ofMinutes(5))
    }

    @Bean
    fun tddOrchestrator(
        llmAdapter: LlmAdapter,
        codeRunner: CodeRunner,
        codeInserter: CodeInserter
    ): TddOrchestrator {
        return TddOrchestrator(llmAdapter, codeRunner, codeInserter)
    }
}
