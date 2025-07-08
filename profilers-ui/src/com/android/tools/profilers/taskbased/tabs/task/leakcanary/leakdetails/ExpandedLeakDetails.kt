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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_CLOSE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_LEAKING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_MORE_INFO
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_NOT_LEAKING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_OPEN
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_REFERENCING_FIELD
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_REFERENCING_OBJECTS
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_RETAINED_BYTES
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_WHY
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.getLeakStatusText
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
@Composable
fun LeakNodeDetails(node: Node, modifier: Modifier = Modifier) {
  var showMoreInfo by remember { mutableStateOf(true) }
  val rowClickableModifier = Modifier
    .clickable(onClick = { showMoreInfo = !showMoreInfo }, indication = null, interactionSource = remember { MutableInteractionSource() })
    .pointerHoverIcon(PointerIcon.Hand)
  Row(modifier = modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
    Column(modifier = Modifier.weight(0.10f)) {
      DetailedHeaderText(LEAKCANARY_LEAKING)
      Spacer(modifier = Modifier.height(5.dp))
      DetailRow(
        icon = { LeakIcon(node.leakingStatus) },
        text = getLeakStatusText(node.leakingStatus)
      )
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(start = 24.dp)
    ) {
      DetailedHeaderText(LEAKCANARY_WHY)
      Spacer(modifier = Modifier.height(5.dp))
      Text(
        text = if (node.leakingStatusReason.isNullOrBlank()) LEAKCANARY_NOT_LEAKING else node.leakingStatusReason,
        fontWeight = FontWeight.Thin,
        modifier = Modifier.padding(bottom = 8.dp)
      )
      node.referencingField?.let {
        DetailText("$LEAKCANARY_REFERENCING_FIELD$it")
      }
      node.retainedByteSize?.let {
        DetailText("$LEAKCANARY_RETAINED_BYTES$it bytes")
      }
      node.retainedObjectCount?.let {
        DetailText("$LEAKCANARY_REFERENCING_OBJECTS$it")
      }
      if (node.notes.isNotEmpty()) {
        Row(modifier = Modifier.testTag(node.className)) {
          Row(modifier = rowClickableModifier) {
            if (showMoreInfo) {
              Icon(AllIconsKeys.General.ArrowDown, LEAKCANARY_OPEN)
            }
            else {
              Icon(AllIconsKeys.General.ArrowRight, LEAKCANARY_CLOSE)
            }
          }
          Column {
            Row (modifier = rowClickableModifier.fillMaxWidth()){
              Spacer(modifier = Modifier.width(TaskBasedUxDimensions.LEAKCANARY_MORE_INFO_TITLE_HORIZONTAL_SPACING_DP))
              Text(LEAKCANARY_MORE_INFO)
              Spacer(modifier = Modifier.width(TaskBasedUxDimensions.LEAKCANARY_MORE_INFO_TITLE_HORIZONTAL_SPACING_DP))
              Divider(orientation = Orientation.Horizontal,
                      modifier = Modifier.fillMaxWidth().align(CenterVertically),
                      color = Color.LightGray,
                      thickness = TaskBasedUxDimensions.LEAKCANARY_MORE_INFO_LINE_THICKNESS_DP
              )
            }
            if(showMoreInfo) {
              BulletList(items = node.notes)
            }
          }
        }
      }
    }
  }
}
@Composable
fun DetailedHeaderText(text: String) {
  Text(text = text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}
/**
 * Composable function to display a row with an icon and text.
 *
 * @param icon The composable function to render the icon.
 * @param text The text to display next to the icon.
 */
@Composable
fun DetailRow(icon: @Composable () -> Unit, text: String) {
  Row {
    icon()
    Spacer(modifier = Modifier.width(8.dp))
    Text(text)
  }
}
@Composable
fun DetailText(text: String) {
  Text(text = text, modifier = Modifier.padding(bottom = 8.dp))
}