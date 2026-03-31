package shizo.module.impl.dungeon.croesus

enum class CroesusState {

    IDLE,
    SEARCHING_NPC,
    WAITING_FOR_MAIN,
    SCANNING_PAGES,
    WAITING_FOR_PAGE,
    ANALYZING_RUN,
    REROLLING,
    WAITING_FOR_CHEST,
    CLAIMING
}