package com.zachgrosser.house

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShortcutRow(
    val shortcut: Shortcut,
    // null until loaded, or if the entity is gone
    val state: String? = null,
    val busy: Boolean = false,
) {
    val status: Status? get() = state?.let { s -> EntityKind.of(shortcut.entityId)?.let { statusOf(it, s) } }
}

data class HomeState(
    val loaded: Boolean = false,
    val connected: Boolean = false,
    val rows: List<ShortcutRow> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

class HomeViewModel(private val store: HouseStore) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    private var api: HomeAssistantApi? = null
    private var refreshJob: Job? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        reload()
    }

    fun reload() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val settings = store.load()
            api = if (settings.isConnected) HomeAssistantApi(settings.address!!, settings.token!!) else null
            val previous = _state.value.rows.associateBy { it.shortcut.entityId }
            _state.value = HomeState(
                loaded = true,
                connected = settings.isConnected,
                rows = settings.shortcuts.map { ShortcutRow(it, state = previous[it.entityId]?.state) },
                refreshing = settings.isConnected && settings.shortcuts.isNotEmpty(),
            )
            refreshStates()
        }
    }

    private suspend fun refreshStates() {
        val api = api ?: return
        val ids = _state.value.rows.map { it.shortcut.entityId }
        if (ids.isEmpty()) return
        api.states(ids).fold(
            onSuccess = { states ->
                _state.update { current ->
                    current.copy(
                        rows = current.rows.map { row -> row.copy(state = states[row.shortcut.entityId]?.state) },
                        refreshing = false,
                        message = null,
                    )
                }
            },
            onFailure = { error ->
                _state.update { it.copy(refreshing = false, message = errorMessage(error)) }
            },
        )
    }

    fun tap(entityId: String) {
        val api = api ?: return
        val row = _state.value.rows.firstOrNull { it.shortcut.entityId == entityId } ?: return
        if (row.busy) return
        val call = tapAction(entityId, row.state ?: return) ?: return
        val before = row.state
        // Show the expected state right away, then use what Home Assistant reports.
        setRow(entityId) { it.copy(busy = true, state = expectedState(call) ?: it.state) }
        viewModelScope.launch {
            api.call(call).fold(
                onSuccess = { updated ->
                    val confirmed = updated ?: api.states(listOf(entityId)).getOrNull()?.get(entityId)
                    setRow(entityId) { it.copy(busy = false, state = confirmed?.state ?: it.state) }
                    _state.update { it.copy(message = null) }
                },
                onFailure = { error ->
                    setRow(entityId) { it.copy(busy = false, state = before) }
                    _state.update { it.copy(message = errorMessage(error)) }
                },
            )
        }
    }

    private fun setRow(entityId: String, change: (ShortcutRow) -> ShortcutRow) {
        _state.update { current ->
            current.copy(rows = current.rows.map { if (it.shortcut.entityId == entityId) change(it) else it })
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(HouseStore(lightContext.filesDir))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        HouseTheme {
            HomeContent(
                state = state,
                onTap = viewModel::tap,
                onRefresh = viewModel::reload,
                onEdit = { navigateTo({ PickerScreen(it) }) },
                onSettings = { navigateTo({ SettingsScreen(it) }) },
            )
        }
    }
}

@Composable
internal fun HomeContent(
    state: HomeState,
    onTap: (String) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("House"),
            rightButton = if (state.connected) LightBarButton.Text("EDIT", onClick = onEdit) else null,
        )
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = unit),
        ) {
            state.message?.let {
                LightText(
                    text = it,
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.padding(end = unit, bottom = unit * 0.5f),
                )
            }
            when {
                !state.loaded -> Unit
                !state.connected -> {
                    Note(
                        "Put a few Home Assistant shortcuts on your Light Phone: lights, switches, scenes and scripts.",
                    )
                    ListRow(title = "Connect to Home Assistant", onClick = onSettings, detail = "Address and access token")
                }
                state.rows.isEmpty() -> Note("Tap EDIT to choose the lights, scenes and switches to show here.")
                else -> state.rows.forEach { row ->
                    ListRow(
                        title = row.shortcut.name,
                        onClick = { onTap(row.shortcut.entityId) },
                        trailing = { StatusView(row.status, busy = row.busy || (state.refreshing && row.state == null)) },
                    )
                }
            }
            Spacer(Modifier.height(unit))
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SETTINGS, onClick = onSettings, contentDescription = "Settings"),
                if (state.connected) {
                    LightBarButton.LightIcon(icon = LightIcons.REFRESH, onClick = onRefresh, contentDescription = "Refresh")
                } else {
                    null
                },
            ),
            modifier = Modifier.padding(bottom = unit),
        )
    }
}
