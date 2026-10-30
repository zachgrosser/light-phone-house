package com.zachgrosser.house

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun HouseTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightThemeTokens.colors.background),
        ) {
            content()
        }
    }
}

fun backButton(onClick: () -> Unit) = LightBarButton.LightIcon(
    icon = LightIcons.BACK,
    onClick = onClick,
    contentDescription = "Back",
)

@Composable
fun MessageScreen(title: String, message: String, onBack: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = onBack?.let { backButton(it) },
            center = LightTopBarCenter.Text(title),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
    }
}

@Composable
fun ListRow(
    title: String,
    onClick: (() -> Unit)?,
    detail: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val unit = 1f.gridUnitsAsDp()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.lightClickable(onClick = onClick) else Modifier)
            .padding(top = unit * 0.5f, bottom = unit * 0.5f, end = unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = title, variant = LightTextVariant.Copy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let {
                LightText(text = it, variant = LightTextVariant.Detail, lighten = true, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(unit))
            // Same height as an icon so all rows line up.
            Box(modifier = Modifier.height(unit * 2f), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}

@Composable
fun StatusView(status: Status?, busy: Boolean) {
    when {
        busy -> LightText(text = "…", variant = LightTextVariant.Copy, lighten = true)
        status == null -> LightText(text = "–", variant = LightTextVariant.Copy, lighten = true)
        status.isOn != null -> LightIcon(
            icon = if (status.isOn) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
            contentDescription = status.label,
        )
        else -> LightText(text = status.label, variant = LightTextVariant.Detail, lighten = status == Status.Unavailable)
    }
}

@Composable
fun ListHeading(text: String) {
    val unit = 1f.gridUnitsAsDp()
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = unit, bottom = unit * 0.25f),
    )
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    LightText(
        text = text,
        variant = LightTextVariant.Paragraph,
        modifier = modifier.padding(end = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
    )
}
