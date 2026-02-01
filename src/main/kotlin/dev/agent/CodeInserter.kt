package dev.agent

/**
 * Abstraction for inserting generated code into source files.
 * Implementations may use IDE PSI, direct file manipulation, or other approaches.
 */
interface CodeInserter {
    /**
     * Insert a test block into the test file.
     * @param testCode The test code to insert (without class wrapper)
     * @return True if insertion was successful
     */
    suspend fun insertTest(testCode: String): Boolean

    /**
     * Insert implementation code into the production file.
     * @param implCode The implementation code to insert (function body or method)
     * @return True if insertion was successful
     */
    suspend fun insertImplementation(implCode: String): Boolean
}
