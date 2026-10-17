package app.stickerdock.duo

/** Product decisions shared by cards, detail actions and their tests. No network or storage. */
object PackPresentation {
    fun matches(pack: StickerPack, query: String, kind: String, category: PackCategory?, stickerPackIds: Set<String>): Boolean =
        (kind == "All" || pack.kind.name.equals(kind, ignoreCase = true)) &&
            (category == null || pack.category == category) &&
            (query.isBlank() || pack.name.contains(query, true) || pack.category.label.contains(query, true) || pack.id in stickerPackIds)

    /** Only bypass the chooser after both installation checks have completed. */
    fun singleTarget(status: Map<WhatsAppTarget, WhatsAppPackStatus>): WhatsAppTarget? =
        if (status.keys.containsAll(WhatsAppTarget.entries))
            WhatsAppTarget.entries.filter { status[it]?.installed == true }.singleOrNull()
        else null

    fun actionLabel(target: WhatsAppTarget?, status: Map<WhatsAppTarget, WhatsAppPackStatus>): String = when {
        target == null -> "Use in WhatsApp"
        status[target]?.added == true -> if (target == WhatsAppTarget.BUSINESS) "Open Business" else "Open WhatsApp"
        target == WhatsAppTarget.BUSINESS -> "Add to Business"
        else -> "Add to WhatsApp"
    }
}
