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
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PickerState {
    data object Loading : PickerState
    data class Failed(val message: String) : PickerState
    data class Ready(val entities: List<Entity>, val chosen: Set<String>) : PickerState
}

/** Groups [entities] under their kind's heading, in [EntityKind] order, alphabetically within each. */
fun groupForPicker(entities: List<Entity>): List<Pair<EntityKind, List<Entity>>> =
    entities.filter { it.kind != null }
        .groupBy { it.kind!! }
        .toSortedMap(compareBy { it.ordinal })
        .map { (kind, list) -> kind to list.sortedBy { it.name.lowercase() } }

class PickerViewModel(private val store: HouseStore) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<PickerState>(PickerState.Loading)
    val state: StateFlow<PickerState> = _state

    init {
        viewModelScope.launch {
            val settings = store.load()
            if (!settings.isConnected) {
                _state.value = PickerState.Failed("Connect to Home Assistant in Settings first.")
                return@launch
            }
            _state.value = HomeAssistantApi(settings.address!!, settings.token!!).supportedEntities().fold(
                onSuccess = { entities -> PickerState.Ready(entities, settings.shortcuts.map { it.entityId }.toSet()) },
                onFailure = { PickerState.Failed(errorMessage(it)) },
            )
        }
    }

    /** Adds the entity to the main screen, or takes it off. Saved straight away. */
    fun toggle(entity: Entity) {
        viewModelScope.launch {
            val settings = store.toggleShortcut(Shortcut(entity.id, entity.name))
            val current = _state.value as? PickerState.Ready ?: return@launch
            _state.value = current.copy(chosen = settings.shortcuts.map { it.entityId }.toSet())
        }
    }
}

/** Chooses which lights, switches, scenes and so on appear on the main screen. */
class PickerScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, PickerViewModel>(sealedActivity) {

    override val viewModelClass: Class<PickerViewModel>
        get() = PickerViewModel::class.java

    override fun createViewModel() = PickerViewModel(HouseStore(lightContext.filesDir))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        HouseTheme {
            when (val current = state) {
                PickerState.Loading -> MessageScreen(title = "Shortcuts", message = "Loading…", onBack = { goBack() })
                is PickerState.Failed -> MessageScreen(title = "Shortcuts", message = current.message, onBack = { goBack() })
                is PickerState.Ready -> PickerContent(current, onToggle = viewModel::toggle, onBack = { goBack() })
            }
        }
    }
}

@Composable
internal fun PickerContent(state: PickerState.Ready, onToggle: (Entity) -> Unit, onBack: () -> Unit) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text("Shortcuts"))
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = unit),
        ) {
            val groups = groupForPicker(state.entities)
            if (groups.isEmpty()) {
                Note("Home Assistant has no lights, switches, scenes or scripts House can show.")
            } else {
                Note("Tick what you'd like on House's main screen. They appear in the order you tick them.")
            }
            groups.forEach { (kind, entities) ->
                ListHeading(kind.heading)
                entities.forEach { entity ->
                    val chosen = entity.id in state.chosen
                    ListRow(
                        title = entity.name,
                        detail = describe(entity),
                        onClick = { onToggle(entity) },
                        trailing = {
                            LightIcon(
                                icon = if (chosen) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                                contentDescription = if (chosen) "Shown" else "Not shown",
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(unit * 2f))
        }
    }
}
