package shizo.module.impl.kuudra.qolplus.vesuvius

enum class State {
    IDLE,
    SEARCHING_NPC,
    WAITING_FOR_GUI,
    SCANNING_PAGES,
    WAITING_FOR_PAGE,
    OPENING_RUN,
    ANALYZING_CHEST,
    CLAIMING,
    FINISHED
}