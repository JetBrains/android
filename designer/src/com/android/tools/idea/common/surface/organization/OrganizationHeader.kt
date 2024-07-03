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
package com.android.tools.idea.common.surface.organization

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.compose.StudioTheme
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

private val toolbarSpacing = 6.dp
private val fontSize = UIUtil.getFontSize(UIUtil.FontSize.SMALL)
private const val iconOpened = "expui/general/chevronDown.svg"
private const val iconClosed = "expui/general/chevronRight.svg"
private const val descriptionOpened = "Hide preview group"
private const val descriptionClosed = "Show preview group"
private val iconClass = AllIcons::General::class.java

@Composable
fun OrganizationHeader(group: OrganizationGroup) {
  val opened = group.isOpened.collectAsState()
  val displayName = group.displayName.collectAsState()

  IconButton(
    modifier =
      Modifier.testTag("openButton")
        .height(ActionToolbarImpl.DEFAULT_MINIMUM_BUTTON_SIZE.height.dp),
    onClick = { group.setOpened(!opened.value) },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (opened.value) Icon(iconOpened, descriptionOpened, iconClass)
      else Icon(iconClosed, descriptionClosed, iconClass)

      Spacer(Modifier.width(toolbarSpacing))

      Text(
        displayName.value,
        modifier = Modifier.testTag("displayName"),
        color = AdtUiUtils.HEADER_COLOR.toComposeColor(),
        fontSize = TextUnit(fontSize, TextUnitType.Sp),
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.width(toolbarSpacing))
    }
  }
}

/** Wraps [OrganizationHeader] into [JComponent]. */
@OptIn(ExperimentalJewelApi::class)
fun createOrganizationHeader(group: OrganizationGroup): JComponent {
  enableNewSwingCompositing()
  return ComposePanel().apply { setContent { StudioTheme { OrganizationHeader(group) } } }
}

fun createTestOrganizationHeader(group: OrganizationGroup): JComponent {
  return JBLabel(group.displayName.value)
}
