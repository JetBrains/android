/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Displays a search bar where the search term is controlled by the [searchFieldTextState]. */
@Composable
fun SearchBar(
  searchFieldTextState: TextFieldState,
  searchFieldPlaceholder: String,
  modifier: Modifier = Modifier,
) {
  TextField(
    searchFieldTextState,
    leadingIcon = {
      Icon(
        StudioIconsCompose.Common.Search,
        contentDescription = "Search",
        modifier.padding(end = 4.dp),
      )
    },
    trailingIcon =
      (@Composable {
          Icon(
            AllIconsKeys.General.CloseSmall,
            contentDescription = "Clear search",
            modifier
              .clickable(onClick = { searchFieldTextState.setTextAndPlaceCursorAtEnd("") })
              .pointerHoverIcon(PointerIcon.Default),
          )
        })
        .takeIf { searchFieldTextState.text.isNotEmpty() },
    placeholder = { Text(searchFieldPlaceholder, fontWeight = FontWeight.Light) },
    modifier = modifier,
  )
}
