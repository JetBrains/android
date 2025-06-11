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
package com.android.tools.idea.wear.dwf.inspections

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnknownWFFVersionInspectionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val fixture
    get() = projectRule.fixture

  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    fixture.enableInspections(UnknownWFFVersionInspection())
  }

  @Test
  fun `unknown WFF version is reported`() {
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version"
                   <warning descr="Unknown Watch Face Format version">android:value="99"</warning> />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `known WFF version`() {
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version" android:value="1" />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `non-integer values are not reported`() {
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version" android:value="not-an-integer" />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `unknown WFF version is reported when using a placeholder`() {
    setManifestPlaceholders(mapOf("wff_version" to "99"))
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version"
                  <warning descr="Unknown Watch Face Format version">android:value="${'$'}{wff_version}"</warning> />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `known WFF version using a placeholder`() {
    setManifestPlaceholders(mapOf("wff_version" to "1"))
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version" android:value="${'$'}{wff_version}" />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  @Test
  fun `non-integer values are not reported using a placeholder`() {
    setManifestPlaceholders(mapOf("wff_version" to "not-an-integer"))
    fixture.configureByText(
      SdkConstants.ANDROID_MANIFEST_XML,
      // language=XML
      """
       <manifest xmlns:android="http://schemas.android.com/apk/res/android">
           <application>
               <property android:name="com.google.wear.watchface.format.version" android:value="${'$'}{wff_version}" />
           </application>
       </manifest>
    """
        .trimIndent(),
    )

    fixture.checkHighlighting(true, false, false)
  }

  private fun setManifestPlaceholders(placeholders: Map<String, String>) {
    val projectSystem = project.getProjectSystem()
    val moduleSystem = projectSystem.getModuleSystem(projectRule.module)
    ApplicationManager.getApplication().invokeAndWait {
      ProjectSystemService.getInstance(project)
        .replaceProjectSystemForTests(
          projectSystem.withModuleSystem(
            moduleSystem.withManifestOverrides(ManifestOverrides(placeholders = placeholders))
          )
        )
    }
  }

  private fun AndroidProjectSystem.withModuleSystem(moduleSystem: AndroidModuleSystem) =
    object : AndroidProjectSystem by this {
      override fun getModuleSystem(module: Module) =
        if (module == moduleSystem.module) moduleSystem
        else this@withModuleSystem.getModuleSystem(module)
    }

  private fun AndroidModuleSystem.withManifestOverrides(manifestOverrides: ManifestOverrides) =
    object : AndroidModuleSystem by this {
      override fun getManifestOverrides() = manifestOverrides
    }
}
