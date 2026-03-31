package shizo.events.core

import shizo.Shizo.logger

abstract class CancellableEvent : Event() {
    var isCancelled = false
        private set

    fun cancel() {
        isCancelled = true
    }

    override fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logger.error(it.message, it)
        }
        return isCancelled
    }
}