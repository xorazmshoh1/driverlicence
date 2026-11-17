package uz.xumoyun.prava.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import uz.xumoyun.core.ads.AdsManager
import uz.xumoyun.core.ui.theme.AppPalette
import uz.xumoyun.core.ui.theme.AppTheme

/** TZ: asosiy rang — yo'l belgisi ko'ki #1565C0, urg'u — sariq #FFB300. */
val PravaPalette = AppPalette(
    seed = Color(0xFF1565C0),
    accent = Color(0xFFFFB300),
    success = Color(0xFF2E7D32),
    failure = Color(0xFFC62828),
)

val LocalAds = staticCompositionLocalOf<AdsManager?> { null }

/** Kontent tili: ruscha interfeysda — ruscha matn, aks holda o'zbekcha. */
@Composable
@ReadOnlyComposable
fun isRussian(): Boolean = LocalConfiguration.current.locales[0].language == "ru"

@Composable
@ReadOnlyComposable
fun loc(uz: String, ru: String): String = if (isRussian()) ru else uz

@Composable
fun PravaPreview(content: @Composable () -> Unit) {
    AppTheme(PravaPalette) { Surface { content() } }
}
