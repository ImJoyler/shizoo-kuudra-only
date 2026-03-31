package shizo.utils.clock

import shizo.events.RenderEvent
import shizo.events.core.on
import java.util.ArrayList

/**
 * Simplified Executor class that allows repeating execution of code without extension functions.
 * @param delay Lambda that returns the delay in milliseconds.
 * @param profileName A profile name for profiling.
 * @param func A standard lambda function to run repeatedly.
 */
open class Executor(
    val delay: () -> Long,
    private val profileName: String = "Unspecified shizo executor",
    val func: () -> Unit
) {

    constructor(delay: Long, profileName: String = "Unspecified shizo executor", func: () -> Unit)
            : this({ delay }, profileName, func)

    internal val clock = Clock()
    internal var shouldFinish = false

    open fun run(): Boolean {
        if (shouldFinish) return true

        // Exact 1:1 Profiling logic
            if (clock.hasTimePassed(delay(), true)) {
                runCatching {
                    func()  // Directly invoke the function
                }
            }
        return false
    }

    /**
     * LimitedExecutor that stops after a certain number of executions.
     */
    class LimitedExecutor(
        delay: Long,
        private val repeats: Int,
        profileName: String = "Unspecified shizo executor",
        func: () -> Unit
    ) : Executor(delay, profileName, func) {

        private var totalRepeats = 0

        override fun run(): Boolean {
            if (shouldFinish) return true
            if (clock.hasTimePassed(delay(), true)) {
                runCatching {
                    func()
                    if (totalRepeats++ >= repeats) destroyExecutor()  // Stop after the specified repeats
                }
            }
            return false
        }
    }

    fun destroyExecutor() {
        shouldFinish = true
    }

    companion object {
        private val executors = ArrayList<Executor>()

        fun Executor.register() {
            executors.add(this)
        }

        init {
            on<RenderEvent.Extract> {
                executors.removeAll { it.run() }
            }
        }
    }
}