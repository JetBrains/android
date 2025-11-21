/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.tools.idea.model.ManifestPlaceholderResolver
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests using build injected variables into AndroidManifest.xml.
 *
 * This verifies that completion and highlighting from XML DOM stack work as intended.
 *
 * @see ManifestPlaceholderResolver
 * @see ManifestPlaceholderConverter
 */
@RunsInEdt
class AndroidManifestPlaceholderDomTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.BASIC) { root ->
    modifyGradleFiles(root, "[hostName:\"www.example.com\"]")
    }
    fixture.allowTreeAccessForAllFiles()
  }

  @Test
  fun testApplicationIdCompletion() {
    val resolver = ManifestPlaceholderResolver(fixture.module)
    val placeholders: Collection<String> = resolver.placeholders.keys
    assertThat(placeholders).containsExactly("hostName")

    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("src/main/AndroidManifest.xml")
    fixture.configureFromExistingVirtualFile(virtualFile!!)
    fixture.moveCaret("<intent-filter>|")
    fixture.type("\n" +
                   "<data\n android:host=\"\${}\"\n android:pathPrefix=\"/transfer\"\n android:scheme=\"myapp\" />")
    fixture.moveCaret("android:host=\"\${|}\"")

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsAllOf("hostName", "applicationId")
  }

  @Test
  fun testApplicationIdHighlighting() {
    addDataToManifest("applicationId")
    fixture.checkHighlighting()
  }

  @Test
  fun testManifestPlaceholderHighlighting() {
    addDataToManifest("hostName")
    fixture.checkHighlighting()
  }

  private fun addDataToManifest(data: String) {
    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("src/main/AndroidManifest.xml")
    fixture.configureFromExistingVirtualFile(virtualFile!!)
    fixture.moveCaret("<intent-filter>|")
    fixture.type("\n" +
                   "<data\n android:host=\"\${${data}}\"\n android:pathPrefix=\"/transfer\"\n android:scheme=\"myapp\" />")
  }

  private fun modifyGradleFiles(root: File, manifestPlaceholderText: String) {
    val virtualFile = root.toVirtualFile()!!.findFileByRelativePath("build.gradle")!!
    val text = VfsUtil.loadText(virtualFile)
    val position = Regex("defaultConfig \\{").find(text)!!.range.endExclusive
    val newText = text.substring(0, position) + "\n        manifestPlaceholders = $manifestPlaceholderText" + text.substring(position)
    runWriteAction {
      VfsUtil.saveText(virtualFile, newText)
    }
  }
}