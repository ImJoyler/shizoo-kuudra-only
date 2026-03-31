package shizo.events

import net.minecraft.world.BossEvent
import shizo.events.core.CancellableEvent


class RenderBossBarEvent(val bossBar: BossEvent) : CancellableEvent()