package app.stickerdock.duo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppDeepGreen = Color(0xFF075E54)
val WhatsAppDarkBackground = Color(0xFF128C7E)

@Composable
fun StickerDockBrandLockup(compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        val ink = MaterialTheme.colorScheme.onPrimaryContainer
        val lime = MaterialTheme.colorScheme.primaryContainer
        val paper = MaterialTheme.colorScheme.surface
        Canvas(Modifier.size(if (compact) 32.dp else 44.dp)) {
            val u = size.width / 44f
            drawRoundRect(lime, Offset(7f * u, 2f * u),
                Size(35f * u, 35f * u), CornerRadius(9f * u))
            drawRoundRect(paper, Offset(2f * u, 7f * u),
                Size(35f * u, 35f * u), CornerRadius(9f * u))
            val bubble = Path().apply {
                moveTo(12f * u, 16f * u); lineTo(28f * u, 16f * u)
                cubicTo(32f * u, 16f * u, 34f * u, 18f * u, 34f * u, 22f * u)
                lineTo(34f * u, 26f * u); cubicTo(34f * u, 30f * u, 32f * u, 32f * u, 28f * u, 32f * u)
                lineTo(19f * u, 32f * u); lineTo(11f * u, 37f * u); lineTo(13f * u, 31f * u)
                cubicTo(10f * u, 30f * u, 8f * u, 28f * u, 8f * u, 25f * u)
                lineTo(8f * u, 22f * u); cubicTo(8f * u, 18f * u, 10f * u, 16f * u, 12f * u, 16f * u); close()
            }
            drawPath(bubble, ink)
            val bolt = Path().apply {
                moveTo(18f * u, 24f * u); lineTo(24f * u, 18f * u); lineTo(24f * u, 22f * u)
                lineTo(30f * u, 22f * u); lineTo(23f * u, 29f * u); lineTo(23f * u, 25f * u); close()
            }
            drawPath(bolt, lime)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("StickerDock", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            if (!compact) Text("YOUR REACTIONS, READY TO SEND", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** High-contrast, tactile WhatsApp button with official green branding and clear feedback. */
@Composable
fun WhatsAppActionButton(
    stickerCount: Int,
    isAdded: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "press_scale"
    )

    val ready = stickerCount >= 3
    val bgColor = when {
        isAdded -> MaterialTheme.colorScheme.surfaceVariant
        ready -> WhatsAppGreen
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isAdded -> WhatsAppDeepGreen
        ready -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled && ready,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        contentColor = contentColor,
        shadowElevation = if (ready && !isAdded) 2.dp else 0.dp,
        interactionSource = interactionSource,
        modifier = modifier
            .scale(scale)
            .semantics {
                role = Role.Button
                this.contentDescription = if (isAdded) "Added to WhatsApp" else if (ready) "Add pack to WhatsApp" else "Needs ${3 - stickerCount} more stickers for WhatsApp"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                StickerDockNavIcon(if (isAdded) "Check" else "WhatsAppFilled", customColor = contentColor)
            }
            Text(
                text = when {
                    isAdded -> "In WhatsApp ✓"
                    ready -> "+ Add to WhatsApp"
                    else -> "$stickerCount/3 Stickers"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/** Top Status & WhatsApp Sync Hub with instant target switcher and installation detection. */
@Composable
fun WhatsAppConnectHero(
    personalInstalled: Boolean,
    businessInstalled: Boolean,
    activeTarget: WhatsAppTarget,
    onSelectTarget: (WhatsAppTarget) -> Unit,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = WhatsAppGreen) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        StickerDockNavIcon("WhatsAppFilled", customColor = Color.White)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("WhatsApp Handoff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = when {
                            personalInstalled && businessInstalled -> "Both WhatsApp & Business detected"
                            personalInstalled -> "Personal WhatsApp active"
                            businessInstalled -> "WhatsApp Business active"
                            else -> "Install WhatsApp to send packs"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    onClick = onOpenHub,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "Manage",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Target app switcher if multiple installed
            if (personalInstalled && businessInstalled) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WhatsAppTarget.entries.forEach { target ->
                        val selected = activeTarget == target
                        Surface(
                            onClick = { onSelectTarget(target) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) WhatsAppGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.5.dp,
                                if (selected) WhatsAppGreen else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (selected) {
                                    Box(Modifier.size(12.dp)) {
                                        StickerDockNavIcon("Check", customColor = WhatsAppDeepGreen)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    target.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) WhatsAppDeepGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Radiant interactive AI Prompt Studio card. */
@Composable
fun AiStudioCard(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    val quickIdeas = listOf("Cat drinking chai", "Sarcastic boss reaction", "Monday morning mood", "Bollywood drama", "Confused doggo", "Victory dance")

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        StickerDockNavIcon("DiscoverFilled", customColor = MaterialTheme.colorScheme.primary)
                    }
                }
                Column {
                    Text("✦ AI STICKER STUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Turn any thought into a sticker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "Describe a scene, Hinglish reaction or meme idea. The AI studio prepares a reaction sticker for your pack.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                placeholder = { Text("e.g. Sarcastic cat typing on laptop...") },
                singleLine = false,
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Situation inspiration chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Popular reaction prompts:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickIdeas.forEach { idea ->
                        Surface(
                            onClick = { onPromptChange(idea) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                idea,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onGenerate,
                enabled = !busy && promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerDockNavIcon("DiscoverFilled", customColor = MaterialTheme.colorScheme.primaryContainer)
                    Text(if (busy) "Designing sticker…" else "Generate Reaction Sticker")
                }
            }
        }
    }
}

@Composable
fun CreationCard(action: String, title: String, description: String, icon: String, enabled: Boolean,
                 onClick: () -> Unit, prominent: Boolean = true) {
    Surface(shape = MaterialTheme.shapes.extraLarge,
        color = if (prominent) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon(icon) }
                }
                Spacer(Modifier.weight(1f))
                Text(if (prominent) "MAGIC CUTOUT" else "MOTION & LOOPS", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (prominent) Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)) { Text(action) }
            else OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

@Composable
fun LibrarySearch(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                  accessibilityLabel: String = label, onSubmit: (String) -> Unit = {}) {
    val focusManager = LocalFocusManager.current
    TextField(value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(value); focusManager.clearFocus() }),
        leadingIcon = { StickerDockNavIcon("Search") },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") },
            modifier = Modifier.semantics { contentDescription = "Clear search" }) { StickerDockNavIcon("Close") } },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        modifier = modifier.semantics { contentDescription = accessibilityLabel })
}

@Composable
fun ProductAction(title: String, description: String, icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { StickerDockNavIcon(icon) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StickerDockNavIcon("Forward")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsSheet(onAccount: () -> Unit, onImport: () -> Unit, onRoadmap: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Library settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            ProductAction("Account & cloud backup", "Manage your optional private backup", "Cloud", onClick = onAccount)
            ProductAction("Import and backups", "Bring your collection from another phone or file", "Import", onClick = onImport)
            ProductAction("Coming next", "See planned features without mixing unfinished tools into the app", "Forward", onClick = onRoadmap)
            Text("Your packs stay on this device unless you choose to back up or share them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductRoadmapSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Coming next", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("These ideas are visible here so the everyday Library, Create and Discover flows stay focused. They are not active controls yet.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RoadmapItem("Smarter sticker suggestions", "Reviewable tag and caption suggestions for your own images.")
            RoadmapItem("Community profiles", "Creator identity, reports, blocking, age protection and moderation before public uploads.")
            RoadmapItem("More reviewed packs", "Daily source collection feeds private review first; only rights-cleared packs reach Discover.")
            RoadmapItem("Large-library search", "Faster indexed search and richer filters for very large local collections.")
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoadmapItem(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("SOON", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
