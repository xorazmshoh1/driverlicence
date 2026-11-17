package uz.xumoyun.prava

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.xumoyun.prava.data.ContentLoader
import javax.inject.Inject

@HiltAndroidApp
class PravaApp : Application() {
    @Inject lateinit var loader: ContentLoader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _contentReady = MutableStateFlow(false)
    val contentReady: StateFlow<Boolean> = _contentReady.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            loader.ensureLoaded()
            _contentReady.value = true
        }
    }
}
