package com.greenpi.monitor

import android.os.Handler
import android.os.Looper

/**
 * Estado compartilhado entre o servico de coleta e a interface.
 *
 * Servico e Activity vivem no mesmo processo, entao um objeto observavel simples
 * resolve a comunicacao sem a complexidade de um broadcast ou de um binder.
 */
object Repository {

    data class State(
        val running: Boolean = false,
        val sample: EnvironmentSample? = null,
        val mqttConnected: Boolean = false,
        val publishCount: Int = 0,
        val lastError: String? = null
    )

    @Volatile
    var state = State()
        private set

    private val listeners = mutableListOf<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun observe(listener: (State) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
        listener(state)
    }

    fun removeObserver(listener: (State) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun update(transform: (State) -> State) {
        state = transform(state)
        val snapshot = state
        val copy = synchronized(listeners) { listeners.toList() }
        main.post { copy.forEach { it(snapshot) } }
    }
}
