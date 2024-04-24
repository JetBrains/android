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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.NameDetails
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.getApiNameAndDetails
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.items

/** A Composable that allows selection of API level (or latest) via a dropdown. */
@Composable
internal fun ApiFilter(apiFilterState: ApiLevelSelectionState) {
  Column(modifier = Modifier.padding(6.dp)) {
    Text("API")

    Dropdown(
      modifier = Modifier.padding(2.dp),
      menuContent = {
        val apiLevels = buildList {
          add(ApiLevelSelection(null))
          for (apiLevel in
            SdkVersionInfo.HIGHEST_KNOWN_API downTo SdkVersionInfo.LOWEST_ACTIVE_API) {
            add(ApiLevelSelection(apiLevel))
          }
        }
        items(
          apiLevels.size,
          isSelected = { index -> apiLevels[index] == apiFilterState.apiLevel },
          onItemClick = { index -> apiFilterState.apiLevel = apiLevels[index] },
        ) { index ->
          ApiLevel(apiLevels[index])
        }
      },
    ) {
      ApiLevel(apiFilterState.apiLevel)
    }
  }
}

@Composable
internal fun ApiLevel(apiLevel: ApiLevelSelection) {
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

@Stable
internal class ApiLevelSelectionState : RowFilter<DeviceProfile> {
  var apiLevel by mutableStateOf(ApiLevelSelection.Latest)

  override fun apply(device: DeviceProfile): Boolean =
    apiLevel.apiLevel.let { it == null || device.apiRange.contains(it) }
}

/**
 * The API level is either a single specific level or "newest on device", which we represent as null
 * here.
 */
internal class ApiLevelSelection(val apiLevel: Int?) {
  val nameDetails: NameDetails =
    if (apiLevel == null)
      NameDetails("Newest on device", "Show most recent API available on device")
    else
      AndroidVersion(apiLevel)
        .getApiNameAndDetails(includeReleaseName = true, includeCodeName = true)

  companion object {
    val Latest = ApiLevelSelection(null)
  }
}
