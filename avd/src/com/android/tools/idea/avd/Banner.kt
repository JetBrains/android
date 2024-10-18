/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.avd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.IntUiPaletteDefaults
import com.android.tools.adtui.compose.rememberColor
import icons.StudioIconsCompose
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
internal fun WarningBanner(text: String, modifier: Modifier = Modifier) {
  Banner(BannerUi.Warning, StudioIconsCompose.Common.Warning, "Warning", text, modifier)
}

@Composable
internal fun ErrorBanner(
  text: String,
  modifier: Modifier = Modifier,
  rightContent: @Composable () -> Unit = {},
) {
  Banner(BannerUi.Error, StudioIconsCompose.Common.Error, "Error", text, modifier, rightContent)
}

@Composable
private fun Banner(
  colors: Colors,
  key: IconKey,
  contentDescription: String,
  text: String,
  modifier: Modifier = Modifier,
  rightContent: @Composable () -> Unit = {},
) {
  Column(modifier.fillMaxWidth()) {
    Divider(orientation = Orientation.Horizontal, color = colors.border)
    Row(
      modifier = Modifier.background(colors.background).padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(key, contentDescription = contentDescription)
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = text, color = colors.foreground)
      Spacer(modifier = Modifier.weight(1f))
      rightContent()
    }
    Divider(orientation = Orientation.Horizontal, color = colors.border)
  }
}

@Composable
internal fun ProgressIndicatorPanel(text: String, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(4.dp)
  Row(
    modifier
      .background(JewelTheme.globalColors.panelBackground, shape)
      .border(1.dp, JewelTheme.globalColors.outlines.focused, shape)
      .padding(8.dp)
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.size(8.dp))
    Text(text)
  }
}

@Composable
internal fun ErrorPanel(modifier: Modifier = Modifier, error: String) {
  val shape = RoundedCornerShape(4.dp)
  Row(
    modifier
      .background(BannerUi.Error.background, shape)
      .border(1.dp, BannerUi.Error.border, shape)
      .padding(8.dp)
  ) {
    Text(error)
  }
}

private fun OpaqueColor(rgb: Int) = Color(rgb.toLong() or 0xff000000)

private object BannerUi {
  object Warning : Colors() {
    override val border: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.warningBorderColor",
          darkFallbackKey = "ColorPalette.Yellow3",
          darkDefault = OpaqueColor(IntUiPaletteDefaults.Dark.Yellow3),
          lightFallbackKey = "ColorPalette.Yellow9",
          lightDefault = OpaqueColor(IntUiPaletteDefaults.Light.Yellow9),
        )

    override val background: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.warningBackground",
          darkFallbackKey = "ColorPalette.Yellow1",
          darkDefault = OpaqueColor(IntUiPaletteDefaults.Dark.Yellow1),
          lightFallbackKey = "ColorPalette.Yellow10",
          lightDefault = OpaqueColor(IntUiPaletteDefaults.Light.Yellow10),
        )

    override val foreground: Color
      @Composable get() = JewelTheme.globalColors.text.normal
  }

  object Error : Colors() {
    override val border: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.errorBorderColor",
          darkFallbackKey = "ColorPalette.Red3",
          darkDefault = JewelTheme.colorPalette.red(3),
          lightFallbackKey = "ColorPalette.Red9",
          lightDefault = JewelTheme.colorPalette.red(9),
        )

    override val background: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.errorBackground",
          darkFallbackKey = "ColorPalette.Red1",
          darkDefault = JewelTheme.colorPalette.red(1),
          lightFallbackKey = "ColorPalette.Red12",
          lightDefault = JewelTheme.colorPalette.red(12),
        )

    override val foreground: Color
      @Composable get() = JewelTheme.globalColors.text.normal
  }
}

private abstract class Colors {
  @get:Composable abstract val border: Color
  @get:Composable abstract val background: Color
  @get:Composable abstract val foreground: Color
}
