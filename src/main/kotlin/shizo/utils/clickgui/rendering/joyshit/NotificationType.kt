package shizo.utils.clickgui.rendering.joyshit

import shizo.utils.Color

enum class NotificationType(val color: Color) {
    INFO(Color(170, 170, 170)),
    SUCCESS(Color(85, 255, 85)),
    WARNING(Color(255, 170, 0)),
    ERROR(Color(255, 85, 85))
}
