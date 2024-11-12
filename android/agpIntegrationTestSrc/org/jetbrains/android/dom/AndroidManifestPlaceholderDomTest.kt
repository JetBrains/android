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
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter

/**
 * Tests using build injected variables into AndroidManifest.xml.
 *
 * This verifies that completion and highlighting from XML DOM stack work as intended.
 *
 * @see ManifestPlaceholderResolver
 * @see ManifestPlaceholderConverter
 */
class AndroidManifestPlaceholderDomTest: AndroidGradleTestCase() {
  private var oldInsertQuotesForAttributeValue = false

  override fun setUp() {
    super.setUp()

    prepareProjectForImport(TestProjectPaths.BASIC)

    oldInsertQuotesForAttributeValue = WebEditorOptions.getInstance().isInsertQuotesForAttributeValue
    WebEditorOptions.getInstance().isInsertQuotesForAttributeValue = false // Android Studio has `false`, IJ has `true`. Use AS defaults.

    modifyGradleFiles("[hostName:\"www.example.com\"]")
    importProject()
    prepareProjectForTest(project, null)
    myFixture.allowTreeAccessForAllFiles()
  }

  override fun tearDown() {
    WebEditorOptions.getInstance().isInsertQuotesForAttributeValue = oldInsertQuotesForAttributeValue
    super.tearDown()
  }

  fun testApplicationIdCompletion() {
    val resolver = ManifestPlaceholderResolver(myFixture.module)
    val placeholders: Collection<String> = resolver.placeholders.keys
    assertThat(placeholders).containsExactly("hostName")

    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("src/main/AndroidManifest.xml")
    myFixture.configureFromExistingVirtualFile(virtualFile!!)
    myFixture.moveCaret("<intent-filter>|")
    myFixture.type("\n" +
                   "<data\n android:host=\"\${}\"\n android:pathPrefix=\"/transfer\"\n android:scheme=\"myapp\" />")
    myFixture.moveCaret("android:host=\"\${|}\"")

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf("hostName", "applicationId")
  }

  fun testApplicationIdHighlighting() {
    addDataToManifest("applicationId")
    myFixture.checkHighlighting()
  }

  fun testManifestPlaceholderHighlighting() {
    addDataToManifest("hostName")
    myFixture.checkHighlighting()
  }

  private fun addDataToManifest(data: String) {
    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("src/main/AndroidManifest.xml")
    myFixture.configureFromExistingVirtualFile(virtualFile!!)
    myFixture.moveCaret("<intent-filter>|")
    myFixture.type("\n" +
                   "<data\n android:host=\"\${${data}}\"\n android:pathPrefix=\"/transfer\"\n android:scheme=\"myapp\" />")
  }

  private fun modifyGradleFiles(manifestPlaceholderText: String) {
    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("build.gradle")!!
    val text = VfsUtil.loadText(virtualFile)
    val position = Regex("defaultConfig \\{").find(text)!!.range.endExclusive
    val newText = text.substring(0, position) + "\n        manifestPlaceholders = $manifestPlaceholderText" + text.substring(position)
    runWriteAction {
      VfsUtil.saveText(virtualFile, newText)
    }
    refreshProjectFiles()
  }
}