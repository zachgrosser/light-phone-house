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
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val address: String? = null,
    val hasToken: Boolean = false,
    val status: String? = null,
    val checking: Boolean = false,
    // Forget needs a second tap.
    val confirmForget: Boolean = false,
    val showAbout: Boolean = false,
)

class SettingsViewModel(private val store: HouseStore) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch { show(store.load()) }
    }

    private fun show(settings: HouseSettings) {
        _state.update { it.copy(address = settings.address, hasToken = !settings.token.isNullOrEmpty(), confirmForget = false) }
    }

    fun setAddress(typed: String) {
        val address = normalizeAddress(typed)
        if (address == null) {
            _state.update { it.copy(status = "“${typed.trim()}” doesn't look like an address. Try something like homeassistant.local or 192.168.1.20.") }
            return
        }
        viewModelScope.launch {
            val settings = store.update { it.copy(address = address) }
            show(settings)
            checkConnection()
        }
    }

    fun setToken(typed: String) {
        val token = typed.trim()
        if (token.isEmpty()) return
        viewModelScope.launch {
            show(store.update { it.copy(token = token) })
            checkConnection()
        }
    }

    fun setFromCode(scanned: String) {
        val code = parseSetupCode(scanned)
        if (code == null) {
            _state.update { it.copy(status = "That QR code doesn't hold an access token.") }
            return
        }
        viewModelScope.launch {
            show(store.update { it.copy(address = code.address ?: it.address, token = code.token) })
            checkConnection()
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            val settings = store.load()
            if (!settings.isConnected) {
                _state.update {
                    it.copy(status = if (settings.address == null) "Add the address next." else "Add an access token next.")
                }
                return@launch
            }
            _state.update { it.copy(checking = true, status = null) }
            val result = HomeAssistantApi(settings.address!!, settings.token!!).serverInfo()
            _state.update {
                it.copy(
                    checking = false,
                    status = result.fold(
                        onSuccess = { info ->
                            listOfNotNull(
                                "Connected",
                                info.locationName?.let { name -> "to $name" },
                                info.version?.let { v -> "(Home Assistant $v)" },
                            ).joinToString(" ") + "."
                        },
                        onFailure = { error -> errorMessage(error) },
                    ),
                )
            }
        }
    }

    fun forget() {
        if (!_state.value.confirmForget) {
            _state.update { it.copy(confirmForget = true) }
            return
        }
        viewModelScope.launch {
            show(store.clear())
            _state.update { it.copy(status = "Forgotten. House no longer has your address, token or shortcuts.") }
        }
    }

    fun openAbout() = _state.update { it.copy(showAbout = true) }

    override fun onBackPressed(): Boolean {
        if (_state.value.showAbout) {
            _state.update { it.copy(showAbout = false) }
            return true
        }
        return false
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(HouseStore(lightContext.filesDir))

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        HouseTheme {
            if (state.showAbout) {
                AboutContent(onBack = { goBack() })
            } else {
                SettingsContent(
                    state = state,
                    onBack = { goBack() },
                    onEditAddress = {
                        navigateTo(
                            { TextEntryScreen(it, title = "Address", initialValue = state.address.orEmpty()) },
                            viewModel::setAddress,
                        )
                    },
                    onScanToken = { navigateTo({ ScanScreen(it) }, viewModel::setFromCode) },
                    onTypeToken = { navigateTo({ TextEntryScreen(it, title = "Access token", initialValue = "") }, viewModel::setToken) },
                    onCheck = viewModel::checkConnection,
                    onForget = viewModel::forget,
                    onAbout = viewModel::openAbout,
                )
            }
        }
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onBack: () -> Unit,
    onEditAddress: () -> Unit,
    onScanToken: () -> Unit,
    onTypeToken: () -> Unit,
    onCheck: () -> Unit,
    onForget: () -> Unit,
    onAbout: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text("Settings"))
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = unit),
        ) {
            when {
                state.checking -> StatusLine("Checking…")
                state.status != null -> StatusLine(state.status)
            }
            ListHeading("Home Assistant")
            ListRow(title = "Address", detail = state.address ?: "Not set", onClick = onEditAddress)
            ListRow(
                title = "Scan access token",
                detail = if (state.hasToken) "Saved. Scan a QR code to replace it" else "From a QR code",
                onClick = onScanToken,
            )
            ListRow(title = "Type access token", onClick = onTypeToken)
            if (state.address != null || state.hasToken) {
                ListRow(title = "Check connection", onClick = onCheck)
                ListRow(
                    title = if (state.confirmForget) "Tap again to forget" else "Forget this Home Assistant",
                    detail = "Removes the address, token and shortcuts from this phone",
                    onClick = onForget,
                )
            }
            ListHeading("Access tokens")
            Note(
                "Make one in Home Assistant under your profile → Security → Long-lived access tokens. " +
                    "It's only shown once, so copy it.\n\n" +
                    "Scanning a QR code is easiest. Make the code on your own computer, not a website.",
            )
            ListRow(title = "About House", onClick = onAbout)
            Spacer(Modifier.height(unit * 2f))
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    val unit = 1f.gridUnitsAsDp()
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        modifier = Modifier.padding(end = unit, bottom = unit * 0.5f),
    )
}

@Composable
internal fun AboutContent(onBack: () -> Unit) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text("About"))
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = unit),
        ) {
            AboutSection(
                null,
                "A few Home Assistant shortcuts for your Light Phone.",
            )
            AboutSection(
                "Privacy",
                "House only talks to your Home Assistant. Your address, token and shortcuts stay on " +
                    "this phone. No ads or tracking.\n\n" +
                    "To revoke the token, delete it in Home Assistant under your profile → Security.",
            )
            AboutSection(
                "Trademark",
                "Home Assistant is a trademark of the Open Home Foundation. House isn't affiliated " +
                    "with or endorsed by Home Assistant or Light.",
            )
            AboutSection(
                "Open source",
                "MIT licensed: $SOURCE_CODE_URL\n\n" +
                    "Version ${BuildConfig.VERSION_NAME}",
            )
            Spacer(Modifier.height(unit * 2f))
        }
    }
}

const val SOURCE_CODE_URL = "github.com/zachgrosser/light-phone-house"

@Composable
private fun AboutSection(title: String?, body: String) {
    val unit = 1f.gridUnitsAsDp()
    title?.let {
        LightText(
            text = it,
            variant = LightTextVariant.Subheading,
            modifier = Modifier.padding(top = unit, bottom = unit * 0.25f),
        )
    }
    LightText(
        text = body,
        variant = LightTextVariant.Paragraph,
        modifier = Modifier.padding(end = unit, bottom = unit * 0.5f),
    )
}
