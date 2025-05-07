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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.JourneyActionArtifacts
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.BorderLayout
import java.io.File
import javax.swing.JOptionPane
import javax.swing.JPanel

class JourneysResultsPanel(private val project: Project) : JPanel(BorderLayout()) {

  private val journeyArtifacts: MutableState<List<JourneyActionArtifacts>> =
    mutableStateOf(emptyList())
  private val shouldResetScroll: MutableState<Boolean> = mutableStateOf(false)

  init {
    this.add(
      StudioComposePanel {
        val artifacts by remember { journeyArtifacts }
        val listState = rememberLazyListState()
        val numArtifacts by remember { derivedStateOf { artifacts.size } }

        LaunchedEffect(shouldResetScroll.value) {
          if (shouldResetScroll.value) {
            listState.scrollToItem(0)
            shouldResetScroll.value = false
          }
        }

        Row {
          LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp).weight(1f, fill = true),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            items(count = numArtifacts) { index ->
              val artifact = artifacts[index]
              JourneysResultsView(
                modifier = Modifier.fillMaxHeight(),
                artifact = artifact,
                index = index,
                numEntries = artifacts.size,
                onImageDoubleClicked = {
                  if (artifact.screenshotImage != null) {
                    openImageInEditor(File(artifact.screenshotImage))
                  }
                },
              )
            }
          }
          VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.fillMaxHeight(),
          )
        }
      },
      BorderLayout.CENTER,
    )
  }

  fun updateArtifacts(artifacts: List<JourneyActionArtifacts>) {
    if (!artifacts.take(journeyArtifacts.value.size).contentEquals(journeyArtifacts.value)) {
      shouldResetScroll.value = true
    }

    journeyArtifacts.value = artifacts
  }

  private fun List<JourneyActionArtifacts>.contentEquals(
    other: List<JourneyActionArtifacts>
  ): Boolean {
    if (this.size != other.size) return false
    return this.zip(other).all { (a, b) -> a == b }
  }

  private fun openImageInEditor(imageFile: File) {
    val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(imageFile)
    if (virtualFile == null) {
      JOptionPane.showMessageDialog(
        this,
        "Image file not found: " + imageFile.absolutePath,
        "Error",
        JOptionPane.ERROR_MESSAGE,
      )
      return
    }

    val descriptor = OpenFileDescriptor(project, virtualFile)
    FileEditorManager.getInstance(project).openEditor(descriptor, true)
  }
}
