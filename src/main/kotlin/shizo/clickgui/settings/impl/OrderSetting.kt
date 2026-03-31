package shizo.clickgui.settings.impl

import shizo.clickgui.settings.Setting

class OrderSetting(
    name: String,
    override val default: Map<String, String>,
    val valuesPerRow: Int = 2,
    var options: List<String> = listOf(),
    description: String? = null,
    var onGuiClosed: (OrderSetting.() -> Unit)? = null,
    var updateAction: (OrderSetting.() -> Unit)? = null
) : Setting<Map<String, String>>(name, description ?: "") {

    override var value: Map<String, String> = default

    var values: List<String>
        get() = value.values.toList()
        set(values) {
            val keys = value.keys.toList()
            val newValues = values.take(keys.size).plus(List(keys.size - values.size) { "" })
            value = keys.zip(newValues).toMap()
        }

    var placeholders: List<String>
        get() = value.keys.toList()
        set(keys) {
            val values = value.values.toList()
            val newKeys = keys.take(values.size).plus(List(values.size - keys.size) { "key_${it}" })
            value = newKeys.zip(values).toMap()
        }

    fun update() {
        updateAction?.invoke(this)
    }

    fun onGuiClosed() {
        onGuiClosed?.invoke(this)
    }
}