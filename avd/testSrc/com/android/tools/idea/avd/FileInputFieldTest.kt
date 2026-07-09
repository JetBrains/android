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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.recordExistingFile
import com.android.testutils.file.someRoot
import com.android.tools.adtui.compose.LocalFileSystem
import com.android.tools.adtui.compose.LocalProject
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.LightVirtualFile
import java.awt.Component
import java.nio.file.Path
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock

class FileInputFieldTest {
  private val fileSystem = createInMemoryFileSystem()

  @Volatile var fileResult: Path? = null
  val fileChooserRule =
    ApplicationServiceRule(
      FileChooserFactory::class.java,
      object : FileChooserFactoryImpl() {
        override fun createFileChooser(
          descriptor: FileChooserDescriptor,
          project: Project?,
          parent: Component?,
        ): FileChooserDialog {
          val virtualFile =
            fileResult?.let { path ->
              object : LightVirtualFile(path.toString()) {
                override fun toNioPath(): Path = path
              }
            }
          return FileChooserDialog { _, _ ->
            if (virtualFile == null) emptyArray() else arrayOf(virtualFile)
          }
        }
      },
    )
  val composeRule = createStudioComposeTestRule()
  @get:Rule
  val rules =
    RuleChain.emptyRuleChain().around(ApplicationRule()).around(fileChooserRule).around(composeRule)

  @Test
  fun selectFileViaDialog() {
    fileResult = fileSystem.someRoot.resolve("tmp").resolve("image.png").recordExistingFile()
    var pathState by mutableStateOf<Path?>(null)
    setContent {
      FileInputField(pathState, { pathState = it }, FileChooserDescriptorFactory.singleFile())
    }

    composeRule.onNodeWithContentDescription("select file").performClick()
    composeRule.waitForIdle()

    assertThat(pathState).isEqualTo(fileResult)
  }

  @Test
  fun updateFileViaTextField() {
    var pathState by mutableStateOf<Path?>(null)
    setContent {
      FileInputField(pathState, { pathState = it }, FileChooserDescriptorFactory.singleFile())
    }

    val path = fileSystem.someRoot.resolve("foo/bar")
    composeRule.onNodeWithTag("FileInputField").performTextReplacement("  $path  ")
    composeRule.waitForIdle()

    assertThat(pathState).isEqualTo(path)
  }

  private fun setContent(composable: @Composable () -> Unit) {
    composeRule.setContent {
      CompositionLocalProvider(
        @OptIn(ExperimentalJewelApi::class) LocalComponent provides mock<JComponent>(),
        LocalProject provides null,
        LocalFileSystem provides fileSystem,
      ) {
        composable()
      }
    }
  }
}
