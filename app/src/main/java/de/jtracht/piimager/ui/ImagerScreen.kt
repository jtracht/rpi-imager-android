package de.jtracht.piimager.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jtracht.piimager.BuildConfig
import de.jtracht.piimager.R
import de.jtracht.piimager.data.ImagingService
import de.jtracht.piimager.data.PiModel
import de.jtracht.piimager.data.Storage
import de.jtracht.piimager.data.WriteState
import de.jtracht.piimager.presentation.ImagerViewModel
import de.jtracht.piimager.presentation.ScreenState
import de.jtracht.piimager.presentation.WizardStep

private val Raspberry = Color(0xFFC51A4A)
private val LightColors = lightColorScheme(primary = Raspberry, secondary = Color(0xFF42684A), background = Color(0xFFF5F3EF), surface = Color(0xFFFFFBF7))
private val DarkColors = darkColorScheme(primary = Color(0xFFFFB1C4), secondary = Color(0xFFAACFAF), background = Color(0xFF161316), surface = Color(0xFF211D20))

@Composable fun ImagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

/** From this width the steps are listed in a side rail instead of a horizontal indicator. */
private val RailBreakpoint = 840.dp
private val Gap = 20.dp
/** Keeps step content and running text at a readable line length on large screens. */
private val MaxStepWidth = 720.dp
private val StepTitles = mapOf(WizardStep.Model to R.string.step_model, WizardStep.Image to R.string.step_image,
    WizardStep.Settings to R.string.step_settings, WizardStep.Storage to R.string.step_storage, WizardStep.Write to R.string.step_write)

private fun gutter(width: Dp) = if (width < 600.dp) 24.dp else 32.dp

/** "Raspberry Pi Zero 2 W" → "Pi Zero 2 W", short enough for the stepper label. */
internal fun shortModelName(name: String) = name.removePrefix("Raspberry ").trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagerScreen(model: ImagerViewModel, requestNotifications: () -> Unit, pickImage: () -> Unit, selectTarget: (Storage) -> Unit,
                 startWrite: (Storage, String) -> Unit, cancelWrite: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val write by ImagingService.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val locked = state.preparing || state.probing || write.busy
    val furthest = WizardStep.furthest(state.imageChosen, state.imageReady, targetReady = state.target != null)
    // A screen opened while an image loads or is ready continues there instead of at the start.
    var step by rememberSaveable { mutableStateOf(when {
        write.busy -> WizardStep.Write
        state.preparing || state.prepared != null -> WizardStep.Settings
        else -> WizardStep.Model
    }) }
    // Set when the settings were confirmed while the image was still loading or being customized.
    var advanceWhenReady by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    // A running write always shows its progress. Otherwise never stay on a step whose prerequisite
    // disappeared, e.g. after clearing the image or a USB refresh.
    LaunchedEffect(furthest, write.busy) {
        if (write.busy) step = WizardStep.Write
        else if (step > furthest) step = furthest
    }
    // Choosing an OS or a local image starts loading it; the settings step can be filled in meanwhile.
    var seenSelection by rememberSaveable { mutableIntStateOf(state.selection) }
    LaunchedEffect(state.selection) {
        if (state.selection != seenSelection) {
            seenSelection = state.selection
            advanceWhenReady = false
            requestNotifications()
            step = WizardStep.Settings
        }
    }
    LaunchedEffect(advanceWhenReady, state.imageReady, state.preparing, state.error) {
        if (!advanceWhenReady) return@LaunchedEffect
        when {
            state.imageReady && step == WizardStep.Settings -> { advanceWhenReady = false; step = WizardStep.Storage }
            // Failed or cancelled: stop waiting so the step can be used again.
            state.error != null || (!state.preparing && state.prepared == null && !state.settingsPending) -> advanceWhenReady = false
        }
    }
    val navigate: (WizardStep) -> Unit = { if (!write.busy && it <= furthest) step = it }
    val back: () -> Unit = {
        // Inside the catalog, Back first returns to the parent category.
        if (!(step == WizardStep.Image && model.backCatalog())) step = WizardStep.entries[step.ordinal - 1]
    }
    BackHandler(enabled = step != WizardStep.Model && !write.busy, onBack = back)
    val done: (WizardStep) -> Boolean = { it < furthest || (it == WizardStep.Write && write.phase == R.string.done) }
    val confirmSettings: () -> Unit = {
        try {
            model.confirmSettings()
            settingsError = null
            advanceWhenReady = true
        } catch (e: IllegalArgumentException) { settingsError = e.message }
    }
    val nextAction = when (step) {
        WizardStep.Settings -> NextAction(enabled = state.imageChosen && !advanceWhenReady,
            hint = if (advanceWhenReady) R.string.settings_waiting else null, onNext = confirmSettings)
        WizardStep.Image -> NextAction(enabled = furthest > step, hint = if (furthest > step) null else R.string.next_needs_os) { navigate(WizardStep.Settings) }
        WizardStep.Storage -> NextAction(enabled = furthest > step, hint = if (furthest > step) null else R.string.next_needs_target) { navigate(WizardStep.Write) }
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val gutter = gutter(maxWidth)
            val rail = maxWidth >= RailBreakpoint
            // Five step names need about 110 dp each.
            val allLabels = maxWidth >= 560.dp
            val pager = @Composable { modifier: Modifier ->
                StepPager(step, gutter, modifier, catalog = state.entries) { current ->
                    when (current) {
                        WizardStep.Model -> {
                            LaunchedEffect(Unit) { model.loadModels() }
                            // Choosing a model is the whole step, so the choice itself moves on.
                            ModelStep(state, locked, retry = model::loadModels) { choice -> if (model.selectModel(choice)) navigate(WizardStep.Image) }
                        }
                        WizardStep.Image -> {
                            LaunchedEffect(Unit) { model.browseCatalog() }
                            ImageStep(model, state, locked, pickImage)
                        }
                        WizardStep.Settings -> {
                            LaunchedEffect(Unit) { model.beginSettings(); settingsError = null }
                            SettingsStep(model, state, settingsError)
                        }
                        WizardStep.Storage -> StorageStep(state, locked, model::refresh, selectTarget)
                        WizardStep.Write -> WriteStep(state, write, enabled = !locked && state.prepared != null && state.target != null, { confirm = true }, cancelWrite)
                    }
                }
            }
            // The model step advances on selection and needs no navigation bar.
            val bar = @Composable {
                AnimatedVisibility(visible = step != WizardStep.Model, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    StepNavigation(nextAction, enabled = !write.busy, gutter, back)
                }
            }
            // Once chosen (or skipped past with the default), step 1 shows the model instead of its generic title.
            val modelLabel = state.model?.let { shortModelName(it.name) }
                ?: stringResource(if (step != WizardStep.Model) R.string.model_all else R.string.step_model)
            if (rail) Row(Modifier.fillMaxSize()) {
                StepRail(step, furthest, !write.busy, done, stepSummary(state, write), navigate)
                VerticalDivider()
                Column(Modifier.weight(1f)) { pager(Modifier.weight(1f)); bar() }
            } else Column(Modifier.fillMaxSize()) {
                StepIndicator(step, furthest, !write.busy, done, modelLabel, allLabels, gutter, navigate)
                pager(Modifier.weight(1f))
                bar()
            }
        }
    }
    if (confirm) {
        val target = state.target
        val image = state.prepared
        if (target != null && image != null) AlertDialog(onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_body, "${target.name} [${target.id}, LUN ${target.lun}]", size(target.capacity), image.name)) },
            confirmButton = { TextButton(onClick = { confirm = false; startWrite(target, image.sha256) }, enabled = !locked) { Text(stringResource(R.string.erase_confirm)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
    }
    state.error?.let { message -> AlertDialog(onDismissRequest = model::clearError,
        text = { Text(stringResource(R.string.failed, message)) },
        confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(R.string.close)) } }) }
}

/** One-line status per step for the side rail. */
@Composable private fun stepSummary(state: ScreenState, write: WriteState): Map<WizardStep, String?> = mapOf(
    WizardStep.Model to (state.model?.name ?: stringResource(R.string.model_all)),
    WizardStep.Image to imageName(state)?.let { name ->
        when {
            state.preparing -> "$name · ${stringResource(R.string.image_loading, size(state.bytes))}"
            state.prepared != null -> "$name · ${stringResource(R.string.ready, size(state.prepared.file.length()))}"
            else -> name
        }
    },
    WizardStep.Settings to settingsSummary(state),
    WizardStep.Storage to state.target?.let { "${it.name} · ${size(it.capacity)}" },
    WizardStep.Write to if (!write.busy && write.phase != 0) stringResource(write.phase) else null
)

@Composable private fun imageName(state: ScreenState): String? =
    state.entry?.name ?: state.uri?.let { stringResource(R.string.local_selected) } ?: state.prepared?.name

@Composable private fun settingsSummary(state: ScreenState): String? = when {
    state.customization.enabled -> stringResource(R.string.custom_summary, state.customization.hostname, state.customization.username)
    // Only once the user confirmed the step, not while the form is still being filled in.
    state.settingsConfirmed && state.prepared != null && !state.settingsPending -> stringResource(R.string.settings_none)
    else -> null
}

/** What the Next button of the bottom bar does on the current step; null hides it. */
private class NextAction(val enabled: Boolean, val hint: Int?, val onNext: () -> Unit)

/** Steps slide in the direction of travel and cross-fade; system animation settings scale the duration. */
@Composable private fun StepPager(step: WizardStep, gutter: Dp, modifier: Modifier, catalog: Any?, content: @Composable ColumnScope.(WizardStep) -> Unit) {
    AnimatedContent(targetState = step, modifier = modifier, label = "wizard-step", transitionSpec = {
        val direction = if (targetState > initialState) 1 else -1
        val motion = tween<androidx.compose.ui.unit.IntOffset>(320, easing = FastOutSlowInEasing)
        (slideInHorizontally(motion) { it / 5 * direction } + fadeIn(tween(220, delayMillis = 80)))
            .togetherWith(slideOutHorizontally(motion) { -it / 5 * direction } + fadeOut(tween(140)))
            .using(SizeTransform(clip = false))
    }) { current ->
        val scroll = rememberScrollState()
        // Opening or leaving a catalog category starts at the top of the new list.
        if (current == WizardStep.Image) LaunchedEffect(catalog) { scroll.scrollTo(0) }
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MaxStepWidth).fillMaxWidth().padding(horizontal = gutter, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Gap)) {
                if (current == WizardStep.Model) Intro()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.step_of, current.ordinal + 1, WizardStep.entries.size),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(when (current) {
                        WizardStep.Model -> R.string.step_model_title
                        WizardStep.Image -> R.string.step_image_title
                        WizardStep.Settings -> R.string.custom_title
                        WizardStep.Storage -> R.string.step_storage_title
                        WizardStep.Write -> R.string.step_write_title
                    }), style = MaterialTheme.typography.headlineSmall)
                }
                content(current)
            }
        }
    }
}

@Composable private fun StepNavigation(next: NextAction?, enabled: Boolean, gutter: Dp, back: () -> Unit) {
    val blocked = next?.hint
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints {
            // On phones the hint gets its own line so it never squeezes between the buttons.
            val stacked = maxWidth < 600.dp
            Column(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stacked) blocked?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = back, enabled = enabled) { Text(stringResource(R.string.back)) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (!stacked) blocked?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End) }
                    }
                    if (next != null) Button(onClick = next.onNext, enabled = enabled && next.enabled) { Text(stringResource(R.string.next)) }
                }
            }
        }
    }
}

/** Horizontal stepper for phones and portrait tablets. Reachable steps can be tapped; step 1 is labelled with the model. */
@Composable private fun StepIndicator(step: WizardStep, furthest: WizardStep, enabled: Boolean, done: (WizardStep) -> Boolean,
                                      modelLabel: String, allLabels: Boolean, gutter: Dp, select: (WizardStep) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter - 8.dp, vertical = 8.dp)) {
        WizardStep.entries.forEach { item ->
            val reachable = enabled && item <= furthest
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .stepSemantics(item, step, reachable, select).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Connector(visible = item != WizardStep.entries.first(), active = item <= step, Modifier.weight(1f))
                    StepMarker(item, step, done(item), reachable)
                    Connector(visible = item != WizardStep.entries.last(), active = item < step, Modifier.weight(1f))
                }
                // Narrow phones cannot fit "Betriebssystem" under a marker; there only the model is named,
                // the current step's title is shown in the content anyway.
                if (item == WizardStep.Model || allLabels) Text(if (item == WizardStep.Model) modelLabel else stringResource(StepTitles.getValue(item)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Vertical step list with the current selection of each step, for wide screens. */
@Composable private fun StepRail(step: WizardStep, furthest: WizardStep, enabled: Boolean, done: (WizardStep) -> Boolean,
                                 summaries: Map<WizardStep, String?>, select: (WizardStep) -> Unit) {
    Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
        WizardStep.entries.forEach { item ->
            val reachable = enabled && item <= furthest
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (item == step) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .stepSemantics(item, step, reachable, select).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StepMarker(item, step, done(item), reachable)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(StepTitles.getValue(item)), style = MaterialTheme.typography.titleSmall,
                        color = if (reachable || item == step) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    summaries[item]?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (item != WizardStep.entries.last()) Box(Modifier.padding(start = 29.dp).width(2.dp).height(16.dp)
                .background(connectorColor(item < step)))
        }
    }
}

private fun Modifier.stepSemantics(item: WizardStep, step: WizardStep, reachable: Boolean, select: (WizardStep) -> Unit) =
    selectable(selected = item == step, enabled = reachable, role = Role.Tab) { select(item) }

@Composable private fun StepMarker(item: WizardStep, step: WizardStep, done: Boolean, reachable: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val current = item == step
    val filled = current || done
    val container by animateColorAsState(if (filled) scheme.primary else Color.Transparent, tween(250), label = "marker-fill")
    val outline by animateColorAsState(when {
        filled -> scheme.primary
        reachable -> scheme.primary
        else -> scheme.outlineVariant
    }, tween(250), label = "marker-outline")
    val label = stringResource(StepTitles.getValue(item))
    val description = if (done && !current) "${item.ordinal + 1}. $label · ${stringResource(R.string.step_done)}" else "${item.ordinal + 1}. $label"
    val diameter by animateDpAsState(if (current) 36.dp else 32.dp, tween(250), label = "marker-size")
    // A fixed slot keeps labels and connectors aligned while the current marker grows.
    Box(Modifier.size(36.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(diameter).clip(CircleShape).background(container).border(2.dp, outline, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (done && !current) "✓" else "${item.ordinal + 1}", style = MaterialTheme.typography.labelLarge,
                color = when {
                    filled -> scheme.onPrimary
                    reachable -> scheme.primary
                    else -> scheme.onSurfaceVariant
                })
        }
    }
}

@Composable private fun Connector(visible: Boolean, active: Boolean, modifier: Modifier) {
    val color = connectorColor(active)
    Box(modifier.height(2.dp).background(if (visible) color else Color.Transparent))
}

@Composable private fun connectorColor(active: Boolean): Color {
    val color by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(250), label = "connector")
    return color
}

@Composable private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.unofficial, BuildConfig.UPSTREAM_TAG), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** The models as tappable cards; a tap selects the model and moves on. */
@Composable private fun ModelStep(state: ScreenState, locked: Boolean, retry: () -> Unit, choose: (PiModel?) -> Unit) {
    Text(stringResource(R.string.model_hint), style = MaterialTheme.typography.bodyMedium)
    if (state.entry != null || state.uri != null || state.prepared != null)
        Text(stringResource(R.string.model_change_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val enabled = !locked && !state.catalogBusy
    val options = listOf<Pair<PiModel?, String>>(null to stringResource(R.string.model_all_hint)) + state.models.map { it to it.description }
    BoxWithConstraints {
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (item, description) ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            ModelOption(item?.name ?: stringResource(R.string.model_all), description, state.model == item, enabled) { choose(item) }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (state.models.isEmpty()) {
        if (state.catalogBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        else OutlinedButton(onClick = retry, enabled = !locked) { Text(stringResource(R.string.retry)) }
    }
    Text(stringResource(R.string.limitations), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("${BuildConfig.VERSION_NAME} · ${BuildConfig.UPSTREAM_COMMIT.take(12)}", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The OS catalog as tappable cards, with the local image as the last option. Tapping an image chooses it
 * and starts loading; tapping a category opens it.
 */
@Composable private fun ImageStep(model: ImagerViewModel, state: ScreenState, locked: Boolean, pick: () -> Unit) {
    val entries = remember(state.entries, state.model, state.catalogQuery) { model.visibleEntries(state) }
    val enabled = !locked && !state.catalogBusy
    if (state.imageChosen) ImageStatus(state, locked, model::cancelPreparation, model::reloadImage, model::forgetImage)
    OutlinedTextField(value = state.catalogQuery, onValueChange = model::searchCatalog,
        label = { Text(stringResource(R.string.catalog_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            if (state.catalogQuery.isNotEmpty()) TextButton(onClick = { model.searchCatalog("") }) { Text(stringResource(R.string.search_clear)) }
        }, supportingText = { Text(stringResource(R.string.catalog_search_hint)) })
    if (state.catalogDepth > 0) TextButton(onClick = { model.backCatalog() }, enabled = !state.catalogBusy) { Text("‹ ${stringResource(R.string.catalog_up)}") }
    if (state.catalogBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (entries.isEmpty() && !state.catalogBusy) Text(stringResource(R.string.catalog_empty))
    OptionColumns(entries.map { entry ->
        @Composable {
            OptionCard(entry.name, entry.description, selected = state.entry == entry,
                category = entry.children.isNotEmpty() || entry.sublist.isNotBlank(), enabled) { model.selectEntry(entry) }
        }
    })
    // A local image is offered below the operating systems of the top level.
    if (state.catalogDepth == 0) OptionCard(stringResource(R.string.local_image), stringResource(R.string.local_hint),
        selected = state.uri != null, category = false, enabled = !locked, choose = pick)
}

/** The chosen image and its loading state, with cancel, reload after a cancelled or failed load, and clear. */
@Composable private fun ImageStatus(state: ScreenState, locked: Boolean, cancel: () -> Unit, reload: () -> Unit, clear: () -> Unit) {
    Panel {
        Text(imageName(state) ?: "", style = MaterialTheme.typography.titleMedium)
        when {
            state.preparing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.customization.enabled) stringResource(R.string.image_customizing)
                    else stringResource(R.string.image_loading, size(state.bytes)))
                Text(stringResource(R.string.preparation_background), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
            }
            state.prepared != null -> Text(stringResource(R.string.ready, size(state.prepared.file.length())), color = MaterialTheme.colorScheme.primary)
            else -> Button(onClick = reload, enabled = !locked) { Text(stringResource(R.string.image_reload)) }
        }
        if (!state.preparing) TextButton(onClick = clear, enabled = !locked) { Text(stringResource(R.string.clear_image)) }
    }
}

/** First-boot settings for the chosen image, edited while the image loads. */
@Composable private fun SettingsStep(model: ImagerViewModel, state: ScreenState, error: String?) {
    ImageStatus(state, locked = state.preparing, model::cancelPreparation, model::reloadImage, model::forgetImage)
    if (state.prepared?.settings?.connect == true) Text(stringResource(R.string.connect_reuse_hint), style = MaterialTheme.typography.bodySmall)
    Panel {
        CustomizationForm(state.draft, state.draftFormat, state.draftSection, local = state.uri != null,
            connectAvailable = state.uri != null || state.entry?.capabilities?.contains("rpi_connect") == true,
            error = error, change = model::editConfiguration, changeSection = model::configurationSection)
    }
}

/** Option cards in one column on phones and two on wider content. */
@Composable private fun OptionColumns(cards: List<@Composable () -> Unit>) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 560.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { card -> Box(Modifier.weight(1f).fillMaxHeight()) { card() } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A selectable card; categories show a chevron because they open a list instead of choosing. */
@Composable private fun OptionCard(name: String, description: String, selected: Boolean, category: Boolean, enabled: Boolean, choose: () -> Unit) {
    val border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth().fillMaxHeight(), border = border) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            if (category) Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun StorageStep(state: ScreenState, locked: Boolean, refresh: () -> Unit, select: (Storage) -> Unit) {
    Panel {
        Text(stringResource(R.string.no_usb), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = refresh, enabled = !locked) { Text(stringResource(R.string.refresh)) }
        state.devices.forEach { device ->
            OutlinedButton(onClick = { select(device) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${device.name} · LUN ${device.lun}")
                    Text(device.id, style = MaterialTheme.typography.labelSmall)
                    if (device.capacity > 0) Text(size(device.capacity), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.allow_usb), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (state.probing) CircularProgressIndicator()
        state.target?.let { Text(stringResource(R.string.target, "${it.name} · LUN ${it.lun}", size(it.capacity)), color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun WriteStep(state: ScreenState, write: WriteState, enabled: Boolean, start: () -> Unit, cancel: () -> Unit) {
    Panel {
        SummaryRow(R.string.summary_model, state.model?.name ?: stringResource(R.string.model_all))
        SummaryRow(R.string.summary_image, state.prepared?.let { "${it.name} · ${size(it.file.length())}" } ?: "—")
        SummaryRow(R.string.summary_settings, settingsSummary(state) ?: "—")
        SummaryRow(R.string.summary_target, state.target?.let { "${it.name} · LUN ${it.lun} · ${size(it.capacity)}" } ?: "—")
    }
    Panel {
        Text(stringResource(R.string.write_hint))
        Button(onClick = start, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.write)) }
        if (write.busy) {
            Text(stringResource(write.phase))
            LinearProgressIndicator(progress = { write.progress }, modifier = Modifier.fillMaxWidth())
            Text("${(write.progress * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.working))
            OutlinedButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
        } else if (write.phase != 0) Text(stringResource(write.phase), color = MaterialTheme.colorScheme.primary)
        write.error?.let { Text(stringResource(R.string.failed, it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun SummaryRow(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun ModelOption(name: String, description: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    val border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth().fillMaxHeight(), border = border) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable private fun size(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes)
