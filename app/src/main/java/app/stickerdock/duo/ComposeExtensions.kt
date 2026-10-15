package app.stickerdock.duo

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip as composeClip
import androidx.compose.ui.graphics.Shape

/** Keeps shared UX screen modifiers local to the StickerDock UI package. */
internal fun Modifier.clip(shape: Shape): Modifier = composeClip(shape)
