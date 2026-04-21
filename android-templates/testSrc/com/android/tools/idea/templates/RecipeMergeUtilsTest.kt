/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RecipeMergeUtilsTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule var tmpFolderRule = TemporaryFolder()

  @Test
  fun testMergeManifestAppendsToTailForExistingModule() {
    val mockProjectTemplateData = mock<ProjectTemplateData>()
    val mockModuleTemplateData = mock<ModuleTemplateData>()
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)
    whenever(mockModuleTemplateData.namespace).thenReturn("com.example")
    whenever(mockModuleTemplateData.isNewModule).thenReturn(false)

    val moduleRoot = tmpFolderRule.newFolder("module")
    val manifestFile = File(moduleRoot, "AndroidManifest.xml")

    val renderingContext =
      RenderingContext(
        projectRule.project,
        projectRule.module,
        "merge manifest test",
        mockModuleTemplateData,
        moduleRoot,
        moduleRoot,
        false,
        true,
      )

    val targetXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example">
          <application>
              <activity android:name=".MainActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val sourceXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application>
              <activity android:name=".NewActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val resultXml = mergeXml(renderingContext, sourceXml, targetXml, manifestFile)

    // Verify that NewActivity is AFTER MainActivity for existing module
    assertThat(resultXml).containsMatch("""(?s)MainActivity.*NewActivity""")
  }

  @Test
  fun testMergeManifestPrependsForNewModule() {
    val mockProjectTemplateData = mock<ProjectTemplateData>()
    val mockModuleTemplateData = mock<ModuleTemplateData>()
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)
    whenever(mockModuleTemplateData.namespace).thenReturn("com.example")
    whenever(mockModuleTemplateData.isNewModule).thenReturn(true)

    val moduleRoot = tmpFolderRule.newFolder("module_new")
    val manifestFile = File(moduleRoot, "AndroidManifest.xml")

    val renderingContext =
      RenderingContext(
        projectRule.project,
        projectRule.module,
        "merge manifest test new module",
        mockModuleTemplateData,
        moduleRoot,
        moduleRoot,
        false,
        true,
      )

    val targetXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example">
          <application>
              <activity android:name=".MainActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val sourceXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application>
              <activity android:name=".NewActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val resultXml = mergeXml(renderingContext, sourceXml, targetXml, manifestFile)

    // Verify that NewActivity is BEFORE MainActivity for new module (preserving old behavior)
    assertThat(resultXml).containsMatch("""(?s)NewActivity.*MainActivity""")
  }

  @Test
  fun testMergeManifestRetainsPackageAttribute() {
    val mockProjectTemplateData = mock<ProjectTemplateData>()
    val mockModuleTemplateData = mock<ModuleTemplateData>()
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)
    whenever(mockModuleTemplateData.namespace).thenReturn("com.example")
    whenever(mockModuleTemplateData.isNewModule).thenReturn(false)

    val moduleRoot = tmpFolderRule.newFolder("module_pkg")
    val manifestFile = File(moduleRoot, "AndroidManifest.xml")

    val renderingContext =
      RenderingContext(
        projectRule.project,
        projectRule.module,
        "merge manifest test package",
        mockModuleTemplateData,
        moduleRoot,
        moduleRoot,
        false,
        true,
      )

    val targetXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example">
          <application>
              <activity android:name=".MainActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val sourceXml =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          <application>
              <activity android:name=".NewActivity" />
          </application>
      </manifest>
      """
        .trimIndent()

    val resultXml = mergeXml(renderingContext, sourceXml, targetXml, manifestFile)

    // Verify that the package attribute is present in the result XML
    assertThat(resultXml).contains("package=\"com.example\"")
  }
}
