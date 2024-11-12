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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.rememberColor
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorSolution
import icons.StudioIconsCompose
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.theme.colorPalette

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccelerationErrorBanner(
  accelerationError: AccelerationErrorCode,
  refresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth()) {
    Divider(orientation = Orientation.Horizontal, color = BannerUi.Error.border)
    Row(
      modifier = Modifier.background(BannerUi.Error.background).padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(StudioIconsCompose.Common.Error, contentDescription = "Error")
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = accelerationError.problem, color = BannerUi.Error.foreground)
      Spacer(modifier = Modifier.weight(1f))
      if (accelerationError.solution == AccelerationErrorSolution.SolutionCode.NONE) {
        AccelerationErrorLink(accelerationError, refresh)
      } else {
        Tooltip(tooltip = { Text(accelerationError.solutionMessage) }) {
          AccelerationErrorLink(accelerationError, refresh)
        }
      }
    }
    Divider(orientation = Orientation.Horizontal, color = BannerUi.Error.border)
  }
}

@Composable
private fun AccelerationErrorLink(accelerationError: AccelerationErrorCode, refresh: () -> Unit) {
  val project = LocalProject.current
  Link(
    accelerationError.solution.description,
    onClick = {
      AccelerationErrorSolution.getActionForFix(accelerationError, project, refresh, null).run()
    },
    overflow = TextOverflow.Ellipsis,
  )
}

private object BannerUi {
  object Error {
    val border: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.Error.borderColor",
          darkFallbackKey = "ColorPalette.Red3",
          darkDefault = JewelTheme.colorPalette.red(3),
          lightFallbackKey = "ColorPalette.Red9",
          lightDefault = JewelTheme.colorPalette.red(9),
        )

    val background: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.Error.background",
          darkFallbackKey = "ColorPalette.Red1",
          darkDefault = JewelTheme.colorPalette.red(1),
          lightFallbackKey = "ColorPalette.Red12",
          lightDefault = JewelTheme.colorPalette.red(12),
        )

    val foreground: Color
      @Composable
      get() =
        rememberColor(
          key = "Banner.Error.foreground",
          darkFallbackKey = "ColorPalette.Gray12",
          darkDefault = JewelTheme.globalColors.text.normal,
          lightFallbackKey = "ColorPalette.Gray1",
          lightDefault = JewelTheme.globalColors.text.normal,
        )
  }
}
