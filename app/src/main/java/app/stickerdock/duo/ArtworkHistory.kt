package app.stickerdock.duo

import org.json.JSONArray
import kotlin.math.roundToInt

/** Bounded edit commands, never bitmap copies. One slider drag or caption burst is one undo. */
class ArtworkHistory private constructor(
    val entries: List<ArtworkEdit>,
    val position: Int,
    private val group: String? = null
) {
    constructor() : this(listOf(ArtworkEdit()), 0)
    val edit: ArtworkEdit get() = entries[position]
    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < entries.lastIndex
    val hasChanges: Boolean get() = edit != ArtworkEdit()

    fun update(value: ArtworkEdit, group: String? = null): ArtworkHistory {
        if (value == edit) return this
        val next = if (group != null && group == this.group && position > 0) {
            entries.take(position) + value
        } else entries.take(position + 1) + value
        val bounded = next.takeLast(51)
        return ArtworkHistory(bounded, bounded.lastIndex, group)
    }
    fun endGroup() = ArtworkHistory(entries, position)
    fun undo() = if (canUndo) ArtworkHistory(entries, position - 1) else endGroup()
    fun redo() = if (canRedo) ArtworkHistory(entries, position + 1) else endGroup()

    fun encode(): String = JSONArray().put(position).put(JSONArray().apply {
        entries.forEach { value -> put(JSONArray().put(value.quarterTurns).put(value.mirror)
            .put(value.zoom.toDouble()).put(value.horizontal.toDouble()).put(value.vertical.toDouble())
            .put(value.topText).put(value.bottomText).put(JSONArray().apply {
                value.cutout.forEach { point -> put((point.x * 1024).roundToInt()); put((point.y * 1024).roundToInt()) }
            })) }
    }).toString()

    companion object {
        fun startingAt(edit: ArtworkEdit) = ArtworkHistory(listOf(edit), 0)

        fun decode(raw: String): ArtworkHistory = runCatching { decodeStrict(raw) }.getOrElse { ArtworkHistory() }

        fun decodeStrict(raw: String): ArtworkHistory {
            require(raw.length <= 100_000)
            val root = JSONArray(raw)
            val values = root.getJSONArray(1)
            require(values.length() in 1..51)
            val entries = (0 until values.length()).map { index ->
                val value = values.getJSONArray(index)
                val points = if (value.length() >= 8) value.getJSONArray(7).let { coordinates ->
                    require(coordinates.length() <= ArtworkCutout.MAX_POINTS * 2 && coordinates.length() % 2 == 0)
                    (0 until coordinates.length() step 2).map { CutoutPoint(coordinates.getInt(it) / 1024f, coordinates.getInt(it + 1) / 1024f) }
                } else emptyList()
                ArtworkEdit(value.getInt(0), value.getBoolean(1), value.getDouble(2).toFloat(),
                    value.getDouble(3).toFloat(), value.getDouble(4).toFloat(), value.getString(5), value.getString(6), points).also {
                    require(it.quarterTurns in 0..3 && it.zoom in 1f..3f && it.horizontal in -1f..1f && it.vertical in -1f..1f)
                    require(it.topText.length <= 100 && it.bottomText.length <= 100)
                    require(it.cutout.isEmpty() || ArtworkCutout.isValid(it.cutout))
                }
            }
            val position = root.getInt(0)
            require(position in entries.indices)
            return ArtworkHistory(entries, position)
        }
    }
}
