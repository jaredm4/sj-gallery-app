package app.sjgallery.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sjgallery.R
import app.sjgallery.data.settings.ScaleMode
import app.sjgallery.data.settings.Settings
import app.sjgallery.data.settings.SlideTransition
import app.sjgallery.ui.appViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it.settings) }
    val loaded by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        val settings = loaded ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.slide_duration))
            SlideDurationSlider(seconds = settings.slideSeconds, onChange = viewModel::setSlideSeconds)

            HorizontalDivider()
            SectionHeader(stringResource(R.string.scale_mode))
            RadioRow(
                label = stringResource(R.string.scale_fit),
                selected = settings.scaleMode == ScaleMode.FIT,
                onSelect = { viewModel.setScaleMode(ScaleMode.FIT) },
            )
            RadioRow(
                label = stringResource(R.string.scale_fill),
                selected = settings.scaleMode == ScaleMode.FILL,
                onSelect = { viewModel.setScaleMode(ScaleMode.FILL) },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.transition))
            RadioRow(
                label = stringResource(R.string.transition_none),
                selected = settings.transition == SlideTransition.NONE,
                onSelect = { viewModel.setTransition(SlideTransition.NONE) },
            )
            RadioRow(
                label = stringResource(R.string.transition_crossfade),
                selected = settings.transition == SlideTransition.CROSSFADE,
                onSelect = { viewModel.setTransition(SlideTransition.CROSSFADE) },
            )

            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.optimize_on_import),
                description = stringResource(R.string.optimize_on_import_description),
                checked = settings.optimizeOnImport,
                onCheckedChange = viewModel::setOptimizeOnImport,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SlideDurationSlider(seconds: Int, onChange: (Int) -> Unit) {
    // Track the thumb locally while dragging; persist once the finger lifts.
    var position by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val shown = position.roundToInt()

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(pluralStringResource(R.plurals.seconds, shown, shown))
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position.roundToInt()) },
            valueRange = Settings.MIN_SLIDE_SECONDS.toFloat()..Settings.MAX_SLIDE_SECONDS.toFloat(),
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
