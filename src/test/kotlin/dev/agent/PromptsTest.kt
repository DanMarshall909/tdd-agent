package dev.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldContain

class PromptsTest : BehaviorSpec({
    given("a test prompt builder") {
        `when`("building a test prompt") {
            then("it should be implemented") {
                // TODO: Add prompt building tests
                "placeholder" shouldContain "place"
            }
        }
    }
})
