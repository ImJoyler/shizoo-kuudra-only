package shizo.events.core
import shizo.Shizo.logger

abstract class Event {

    open fun postAndCatch(): Boolean {
        runCatching {
            EventBus.post(this)
        }.onFailure {
            logger.error(it.message, it)
        }
        return false
    }
}