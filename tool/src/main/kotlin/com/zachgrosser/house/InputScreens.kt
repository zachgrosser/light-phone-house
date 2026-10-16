package com.zachgrosser.house

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightThemeTokens

/** Light's full-screen text entry with its on-screen keyboard. Returns what was typed. */
class TextEntryScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val text = rememberTextFieldState(initialValue)
        val keyboardOptions = rememberKeyboardOptions()
        HouseTheme {
            LightTextInputEditor(
                title = title,
                state = text,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack() },
                submitLabel = "SAVE",
                singleLine = true,
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}

/** Light's QR code scanner. Returns the code's text. Asks for the camera the first time. */
class ScanScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        var scanned by remember { mutableStateOf<String?>(null) }
        HouseTheme {
            LightQrCodeScanner(
                title = "Scan access token",
                onScanned = { scanned = it },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
        // Leave the scanner after it has finished drawing, as the SDK asks.
        LaunchedEffect(scanned) {
            scanned?.let { goBack(it) }
        }
    }
}
