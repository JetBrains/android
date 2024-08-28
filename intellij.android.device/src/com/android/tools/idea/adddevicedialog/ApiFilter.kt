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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.NameDetails
import com.android.sdklib.getApiNameAndDetails
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items

/** A Composable that allows selection of API level via a dropdown. */
@Composable
fun ApiFilter(
  apiLevels: List<AndroidVersion>,
  selectedApiLevel: AndroidVersionSelection,
  onApiLevelChange: (AndroidVersionSelection) -> Unit,
) {
  Column(modifier = Modifier.padding(6.dp)) {
    Text("API")

    Dropdown(
      modifier = Modifier.padding(2.dp),
      menuContent = {
        val apiLevels = apiLevels.map { AndroidVersionSelection(it) }
        items(
          apiLevels.size,
          isSelected = { index -> apiLevels[index] == selectedApiLevel },
          onItemClick = { index -> onApiLevelChange(apiLevels[index]) },
        ) { index ->
          ApiLevel(apiLevels[index])
        }
      },
    ) {
      ApiLevel(selectedApiLevel)
    }
  }
}

@Composable
fun ApiLevel(apiLevel: AndroidVersionSelection) {
  Row {
    Text(
      apiLevel.nameDetails.name,
      Modifier.padding(end = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    apiLevel.nameDetails.details?.let {
      Text(it, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

data class AndroidVersionSelection(private val androidVersion: AndroidVersion) {
  val nameDetails: NameDetails
    get() = androidVersion.getApiNameAndDetails(includeReleaseName = true, includeCodeName = true)

  /** Don't worry about extension levels. */
  fun matches(version: AndroidVersion) =
    androidVersion.apiLevel == version.apiLevel && androidVersion.codename == version.codename
}
