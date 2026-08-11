/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.idea.npw.templateengine.viewmodel.ConfigureProjectViewModel
import com.android.tools.idea.npw.templateengine.viewmodel.ValidationMessage
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path
import kotlin.io.path.exists
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalJewelApi::class)
@Composable
@Suppress("UnstableApiUsage")
fun TemplateEngineConfigureProjectStep(viewModel: ConfigureProjectViewModel) {
  Column(modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 30.dp)) {
    if (viewModel.templateName.isNotEmpty()) {
      Text(text = viewModel.templateName, fontWeight = FontWeight.Bold)
      Spacer(modifier = Modifier.height(4.dp))
    }
    if (viewModel.templateDescription.isNotEmpty()) {
      Text(text = viewModel.templateDescription)
      Spacer(modifier = Modifier.height(28.dp))
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(text = "Name", modifier = Modifier.width(180.dp))
      TextField(state = viewModel.nameState, modifier = Modifier.width(450.dp))
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(text = "Package name", modifier = Modifier.width(180.dp))
      TextField(state = viewModel.packageNameState, modifier = Modifier.width(450.dp))
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(text = "Save location", modifier = Modifier.width(180.dp))
      TextField(
        state = viewModel.locationState,
        modifier = Modifier.width(450.dp),
        trailingIcon = {
          Icon(
            key = AllIconsKeys.Nodes.Folder,
            iconClass = AllIconsKeys::class.java,
            contentDescription = "Browse...",
            modifier =
              Modifier.clickable {
                  val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                  var path: Path? =
                    try {
                      Path.of(viewModel.locationState.text.toString())
                    } catch (e: Exception) {
                      null
                    }
                  while (path != null && !path.exists()) {
                    path = path.parent
                  }
                  val toSelect = path?.let { LocalFileSystem.getInstance().findFileByNioFile(it) }
                  val selected = FileChooser.chooseFile(descriptor, null, toSelect)
                  if (selected != null) {
                    viewModel.locationState.setTextAndPlaceCursorAtEnd(selected.path)
                  }
                }
                .padding(horizontal = 8.dp),
          )
        },
      )
    }
    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(text = "Minimum SDK", modifier = Modifier.width(180.dp))

      Dropdown(
        menuContent = {
          viewModel.availableMinSdks.forEach { item ->
            selectableItem(selected = item == viewModel.selectedMinSdk, onClick = { viewModel.updateSelectedMinSdk(item) }) {
              Text(item.label)
            }
          }
        },
        modifier = Modifier.width(450.dp),
      ) {
        Text(viewModel.selectedMinSdk.label)
      }
    }
    Spacer(modifier = Modifier.height(16.dp))

    viewModel.validationMessage?.let { validation ->
      when (validation) {
        is ValidationMessage.Warning -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              key = AllIconsKeys.General.Warning,
              iconClass = AllIconsKeys::class.java,
              contentDescription = "Warning",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = validation.message, color = JewelTheme.globalColors.text.warning)
          }
        }
        is ValidationMessage.Error -> {
          Text(text = validation.message, color = JewelTheme.globalColors.text.error)
        }
      }
    }
  }
}
