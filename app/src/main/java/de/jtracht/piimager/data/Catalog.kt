package de.jtracht.piimager.data

import de.jtracht.piimager.domain.Customization

data class OsEntry(
    val name: String, val description: String = "", val url: String = "",
    val sha256: String = "", val size: Long = 0, val sublist: String = "",
    val children: List<OsEntry> = emptyList(), val initFormat: String = "",
    val capabilities: Set<String> = emptySet(),
    // Missing devices means unspecified compatibility; an explicit empty list matches no board.
    val devices: Set<String>? = null
) {
    fun validateCustomization(settings: Customization) {
        require(!settings.enabled || !settings.connect || "rpi_connect" in capabilities) { "This catalog image does not advertise Raspberry Pi Connect support" }
    }
}

data class PiModel(val name: String, val description: String, val tags: Set<String>, val inclusive: Boolean)
data class CatalogDocument(val entries: List<OsEntry>, val models: List<PiModel>)

/** Pure selection rules, shared by the UI and the last check before preparation. */
object CatalogSelection {
    fun supports(entry: OsEntry, model: PiModel?): Boolean = model == null || model.tags.isEmpty() ||
        (entry.devices?.any { it in model.tags } ?: model.inclusive)

    fun compatible(entries: List<OsEntry>, model: PiModel?): List<OsEntry> = entries.mapNotNull { entry ->
        when {
            entry.children.isNotEmpty() -> compatible(entry.children, model).takeIf { it.isNotEmpty() }?.let { entry.copy(children = it) }
            // A remote category must remain navigable until its contents can be checked.
            entry.sublist.isNotBlank() && entry.devices == null -> entry
            entry.url.isBlank() && entry.sublist.isBlank() -> null
            supports(entry, model) -> entry
            else -> null
        }
    }

    fun search(entries: List<OsEntry>, query: String): List<OsEntry> {
        val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return entries
        return entries.mapNotNull { entry ->
            val text = "${entry.name} ${entry.description}"
            if (words.all { text.contains(it, ignoreCase = true) }) entry
            else search(entry.children, query).takeIf { it.isNotEmpty() }?.let { entry.copy(children = it) }
        }
    }
}
