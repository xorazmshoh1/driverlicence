package uz.xumoyun.prava.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.xumoyun.core.common.ThemeMode
import uz.xumoyun.core.data.CommonSettings
import uz.xumoyun.core.ui.AppLocale
import uz.xumoyun.core.ui.components.ChoiceRow
import uz.xumoyun.core.ui.components.SettingsRow
import uz.xumoyun.prava.R
import uz.xumoyun.prava.data.PravaSettings
import uz.xumoyun.prava.domain.Category
import uz.xumoyun.prava.ui.LocalAds
import uz.xumoyun.prava.ui.PravaPreview
import javax.inject.Inject

data class PravaSettingsState(val category: Category = Category.B, val theme: ThemeMode = ThemeMode.SYSTEM, val sound: Boolean = true)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: PravaSettings, private val common: CommonSettings) : ViewModel() {
    val state: StateFlow<PravaSettingsState> = combine(settings.category, common.themeMode, settings.soundOn) { c, t, s ->
        PravaSettingsState(c, t, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PravaSettingsState())

    fun setCategory(c: Category) = viewModelScope.launch { settings.setCategory(c) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { common.setThemeMode(t) }
    fun setSound(on: Boolean) = viewModelScope.launch { settings.setSound(on) }
    fun setLanguage(tag: String) = viewModelScope.launch { common.setLanguage(tag) }
    fun finishOnboarding() = viewModelScope.launch { common.setOnboardingDone() }
}

private enum class Dialog { LANGUAGE, CATEGORY, THEME }

private val Languages = listOf("uz" to "Oʻzbekcha", "ru" to "Русский")

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val ads = LocalAds.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val lang = AppLocale.current() ?: if (context.resources.configuration.locales[0].language == "ru") "ru" else "uz"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsRow(stringResource(R.string.language), value = Languages.first { it.first == lang }.second, icon = Icons.Rounded.Language, onClick = { dialog = Dialog.LANGUAGE })
        SettingsRow(stringResource(R.string.category), value = stringResource(R.string.category_value, state.category.name, if (state.category == Category.A1) 16 else 18), icon = Icons.Rounded.DirectionsCar, onClick = { dialog = Dialog.CATEGORY })
        SettingsRow(stringResource(R.string.theme), value = stringResource(themeName(state.theme)), icon = Icons.Rounded.DarkMode, onClick = { dialog = Dialog.THEME })
        SettingsRow(stringResource(R.string.sound), icon = Icons.AutoMirrored.Rounded.VolumeUp, trailing = { Switch(state.sound, onCheckedChange = viewModel::setSound) })
        if (ads != null && activity != null && ads.privacyOptionsRequired) {
            SettingsRow(stringResource(R.string.privacy_options), icon = Icons.Rounded.PrivacyTip, onClick = { ads.showPrivacyOptions(activity) })
        }
        SettingsRow(stringResource(R.string.privacy_policy), icon = Icons.Rounded.Policy, onClick = { openUrl(context, context.getString(R.string.privacy_policy_url)) })
        SettingsRow(stringResource(R.string.rate_app), icon = Icons.Rounded.Star, onClick = {
            openUrl(context, "market://details?id=${context.packageName}", "https://play.google.com/store/apps/details?id=${context.packageName}")
        })
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }

    val close = { dialog = null }
    when (dialog) {
        Dialog.LANGUAGE -> Choice(stringResource(R.string.language), close) {
            Languages.forEach { (tag, name) -> ChoiceRow(name, lang == tag) { viewModel.setLanguage(tag); AppLocale.set(tag); close() } }
        }
        Dialog.CATEGORY -> Choice(stringResource(R.string.category), close) {
            Category.entries.forEach { c ->
                ChoiceRow(stringResource(R.string.category_value, c.name, if (c == Category.A1) 16 else 18), state.category == c) { viewModel.setCategory(c); close() }
            }
        }
        Dialog.THEME -> Choice(stringResource(R.string.theme), close) {
            ThemeMode.entries.forEach { t -> ChoiceRow(stringResource(themeName(t)), state.theme == t) { viewModel.setTheme(t); close() } }
        }
        null -> Unit
    }
}

@Composable
private fun Choice(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun themeName(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun openUrl(context: Context, url: String, fallback: String? = null) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        fallback?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    }
}

/** Birinchi ochilish: logo va o'zbekcha/ruscha tugmalari. */
@Composable
fun LanguageScreen(onPicked: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.heightIn(min = 160.dp),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.choose_language), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onPicked("uz") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Oʻzbekcha") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onPicked("ru") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Русский") }
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguagePreview() { PravaPreview { LanguageScreen {} } }
