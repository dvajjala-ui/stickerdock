package app.stickerdock.duo

import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Preview playback belongs only to the currently resumed screen/dialog. */
@Composable
internal fun AnimateWhileResumed(drawable: AnimatedImageDrawable?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(drawable, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> drawable?.start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> drawable?.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) drawable?.start()
        onDispose { lifecycle.removeObserver(observer); drawable?.stop() }
    }
}
