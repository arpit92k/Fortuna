package io.github.arpit92k.fortuna

import io.github.arpit92k.fortuna.core.session.Session
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The session, as the interface uses it.
 *
 * The session takes its calls one at a time, and some of them take a moment
 * (the PIN's key derivation is meant to take about a quarter of a second), so
 * every call runs on one background thread of its own, in order, and the
 * screens never wait on the main thread.
 */
class Core(
    private val session: Session,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val state: StateFlow<SessionState> get() = session.state

    val dataset get() = session.dataset

    /** Runs [block] on the core's thread and returns its result. */
    suspend fun <T> run(block: Session.() -> T): T = withContext(dispatcher) { session.block() }

    /** Runs [block] on the core's thread without waiting for it. */
    fun post(block: Session.() -> Unit) {
        scope.launch { session.block() }
    }
}
