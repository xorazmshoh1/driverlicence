package uz.xumoyun.prava.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uz.xumoyun.core.ads.BannerAd
import uz.xumoyun.core.ui.AppLocale
import uz.xumoyun.prava.R
import uz.xumoyun.prava.domain.QuizMode
import uz.xumoyun.prava.ui.home.HomeRoute
import uz.xumoyun.prava.ui.home.TicketsRoute
import uz.xumoyun.prava.ui.home.TopicsRoute
import uz.xumoyun.prava.ui.list.QuestionListRoute
import uz.xumoyun.prava.ui.quiz.QuizRoute
import uz.xumoyun.prava.ui.result.ResultRoute
import uz.xumoyun.prava.ui.result.ResultViewModel
import uz.xumoyun.prava.ui.result.ReviewRoute
import uz.xumoyun.prava.ui.settings.LanguageScreen
import uz.xumoyun.prava.ui.settings.SettingsRoute
import uz.xumoyun.prava.ui.settings.SettingsViewModel
import uz.xumoyun.prava.ui.stats.StatsRoute

private object R2 {
    const val LANGUAGE = "language"
    const val HOME = "home"
    const val TICKETS = "tickets"
    const val TOPICS = "topics"
    const val QUIZ = "quiz/{mode}?ticket={ticket}&topic={topic}"
    const val RESULT = "result/{attempt}"
    const val REVIEW = "review/{attempt}"
    const val LIST = "list/{mode}"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    fun quiz(mode: QuizMode, ticket: Int = 0, topic: Int = 0) = "quiz/${mode.name}?ticket=$ticket&topic=$topic"
}

/** Banner ko'rsatiladigan ekranlar (savol ekranida reklama yo'q). */
private val bannerRoutes = setOf(R2.HOME, R2.TICKETS, R2.TOPICS, R2.STATS, R2.LIST)

@Composable
fun PravaRoot(startWithLanguage: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ads = LocalAds.current
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settings by settingsVm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBar(route, nav) },
        bottomBar = { if (route in bannerRoutes && ads != null) BannerAd(ads) },
    ) { padding ->
        NavHost(nav, startDestination = if (startWithLanguage) R2.LANGUAGE else R2.HOME, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(R2.LANGUAGE) {
                LanguageScreen { tag ->
                    settingsVm.setLanguage(tag)
                    settingsVm.finishOnboarding()
                    AppLocale.set(tag)
                    nav.navigate(R2.HOME) { popUpTo(R2.LANGUAGE) { inclusive = true } }
                }
            }
            composable(R2.HOME) {
                HomeRoute(
                    onTickets = { nav.navigate(R2.TICKETS) },
                    onTopics = { nav.navigate(R2.TOPICS) },
                    onExam = { nav.navigate(R2.quiz(QuizMode.EXAM)) },
                    onMistakes = { nav.navigate("list/${QuizMode.MISTAKES.name}") },
                    onFavorites = { nav.navigate("list/${QuizMode.FAVORITES.name}") },
                    onStats = { nav.navigate(R2.STATS) },
                )
            }
            composable(R2.TICKETS) { TicketsRoute(onOpen = { nav.navigate(R2.quiz(QuizMode.TICKET, ticket = it)) }) }
            composable(R2.TOPICS) { TopicsRoute(onOpen = { nav.navigate(R2.quiz(QuizMode.TOPIC, topic = it)) }) }
            composable(
                R2.QUIZ,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("ticket") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("topic") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                QuizRoute(
                    soundOn = settings.sound,
                    onFinished = { id -> nav.navigate("result/$id") { popUpTo(R2.QUIZ) { inclusive = true } } },
                )
            }
            composable(R2.RESULT, arguments = listOf(navArgument("attempt") { type = NavType.LongType })) { backStack ->
                val vm: ResultViewModel = hiltViewModel(backStack)
                ResultRoute(vm, onReview = { nav.navigate("review/${backStack.arguments?.getLong("attempt")}") }, onHome = { nav.popBackStack(R2.HOME, false) })
            }
            composable(R2.REVIEW, arguments = listOf(navArgument("attempt") { type = NavType.LongType })) { ReviewRoute(hiltViewModel()) }
            composable(R2.LIST, arguments = listOf(navArgument("mode") { type = NavType.StringType })) {
                QuestionListRoute(onPractice = { mode -> nav.navigate(R2.quiz(mode)) })
            }
            composable(R2.STATS) { StatsRoute() }
            composable(R2.SETTINGS) { SettingsRoute(settingsVm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(route: String?, nav: NavHostController) {
    if (route == null || route == R2.LANGUAGE) return
    val title = when (route) {
        R2.HOME -> R.string.app_name
        R2.TICKETS -> R.string.tickets
        R2.TOPICS -> R.string.topics
        R2.RESULT -> R.string.result
        R2.REVIEW -> R.string.review_mistakes
        R2.STATS -> R.string.statistics
        R2.SETTINGS -> R.string.settings
        R2.LIST -> R.string.questions
        else -> R.string.test
    }
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            if (route != R2.HOME) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            }
        },
        actions = {
            if (route == R2.HOME) {
                IconButton(onClick = { nav.navigate(R2.SETTINGS) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            }
        },
    )
}
