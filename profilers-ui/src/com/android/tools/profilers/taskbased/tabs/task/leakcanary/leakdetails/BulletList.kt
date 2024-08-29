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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_MORE_INFO_TITLE_PADDING
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_MORE_INFO_TEXT_PADDING
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.LEAKCANARY_BULLET_UNICODE
import org.jetbrains.jewel.ui.component.Text

@Composable
fun BulletList(
  items: List<String>
) {
  Column(modifier = Modifier.padding(start = LEAKCANARY_MORE_INFO_TITLE_PADDING)) {
    items.forEachIndexed { _, item ->
      BulletListItem(item)
    }
  }
}

@Composable
fun BulletListItem(
  text: String
) {
  val bulletStyle = Modifier.padding(end = LEAKCANARY_MORE_INFO_TEXT_PADDING)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = LEAKCANARY_BULLET_UNICODE,
      modifier = bulletStyle,
    )
    Text(text = text)
  }
}