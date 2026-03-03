package app.gamenative.utils.launchdependencies

/**
 * Receives step content and runs it (builds full guest executable, sets on launcher, starts).
 * Implemented by the launch orchestrator; steps call this instead of building commands themselves.
 */
interface StepRunner {
    fun runStepContent(stepContent: String)
}
