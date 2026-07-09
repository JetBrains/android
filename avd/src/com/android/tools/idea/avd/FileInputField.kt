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
package com.android.tools.idea.avd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import com.android.tools.adtui.compose.LocalFileSystem
import com.android.tools.adtui.compose.LocalProject
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import java.nio.file.Path
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** A text field that displays a Path, with a button that launches a file chooser dialog. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileInputField(
  filePath: Path?,
  onPathUpdated: (Path?) -> Unit,
  descriptor: FileChooserDescriptor,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  errorMessage: String? = null,
) {
  @OptIn(ExperimentalJewelApi::class) val component = LocalComponent.current
  val project = LocalProject.current
  val fileSystem = LocalFileSystem.current

  val textState = rememberTextFieldState(filePath?.toString() ?: "")
  LaunchedEffect(Unit) {
    snapshotFlow { textState.text.toString() }
      .collect { onPathUpdated(if (it.isBlank()) null else fileSystem.getPath(it.trim())) }
  }

  ErrorTooltip(errorMessage?.takeIf { enabled }, modifier) {
    TextField(
      textState,
      Modifier.testTag("FileInputField"),
      enabled,
      outline = if (enabled && errorMessage != null) Outline.Error else Outline.None,
      trailingIcon = {
        Icon(
          AllIconsKeys.General.OpenDisk,
          "select file",
          Modifier.padding(start = Padding.MEDIUM_LARGE)
            .clickable(
              enabled,
              onClick = {
                val virtualFile =
                  FileChooser.chooseFile(descriptor, component, project, null) ?: return@clickable
                textState.setTextAndPlaceCursorAtEnd(virtualFile.toNioPath().toString())
              },
            )
            .pointerHoverIcon(PointerIcon.Default),
        )
      },
    )
  }
}
