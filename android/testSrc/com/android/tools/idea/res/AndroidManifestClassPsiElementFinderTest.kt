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
package com.android.tools.idea.res

import com.android.tools.idea.manifest.ManifestClassToken
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiField
import com.intellij.testFramework.ExtensionTestUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertNotNull

private const val PACKAGE_NAME = "com.example"
private val PERMISSIONS = setOf("GREAT_PERMISSION", "OTHER_GREAT_PERMISSION", "OKAY_PERMISSION")

@RunWith(JUnit4::class)
class AndroidManifestClassPsiElementFinderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }
  private val finder by lazy { AndroidManifestClassPsiElementFinder(project) }
  private val facet by lazy { requireNotNull(projectRule.module.androidFacet) }

  private val manifestClassToken = object : ManifestClassToken<AndroidProjectSystem> {
    var shouldGenerateManifestLightClasses = true
    override fun shouldGenerateManifestLightClasses(
      projectSystem: AndroidProjectSystem,
      module: Module) = shouldGenerateManifestLightClasses

    override fun isApplicable(projectSystem: AndroidProjectSystem) = true
  }

  @Before
  fun setUp() {
    ExtensionTestUtil.maskExtensions(
      ManifestClassToken.EP_NAME,
      listOf(manifestClassToken),
      projectRule.testRootDisposable)
  }

  @Test
  fun getManifestClassForFacet() {
    addManifest(PERMISSIONS)
    val manifestClass = runReadAction {
      finder.getManifestClassForFacet(facet)
    }
    assertNotNull(manifestClass)
    val permissionClass = manifestClass.findInnerClassByName("permission", false)
    assertNotNull(permissionClass)
    val allFieldNames = runReadAction {
      permissionClass.allFields.map(PsiField::getName)
    }
    assertThat(allFieldNames).containsExactlyElementsIn(PERMISSIONS)

    val recomputedManifestClass =
      runReadAction {
        finder.getManifestClassForFacet(facet)
      }
    assertThat(recomputedManifestClass).isSameAs(manifestClass)
  }

  @Test
  fun getManifestClassForFacet_noManifestFile() {
    val manifestClass = runReadAction {
      finder.getManifestClassForFacet(facet)
    }

    assertThat(manifestClass).isNull()
  }

  @Test
  fun getManifestClassForFacet_flagNotSet() {
    addManifest(PERMISSIONS)

    manifestClassToken.shouldGenerateManifestLightClasses = false

    val manifestClass = runReadAction {
      finder.getManifestClassForFacet(facet)
    }

    assertThat(manifestClass).isNull()
  }

  fun addManifest(permission: Iterable<String>) {
    val permissionTags = permission.joinToString("\n  ") {
      "<permission android:name=\"com.example.$it\" />"
    }
    fixture.addFileToProject(
      "AndroidManifest.xml",
      // language=xml
      """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="$PACKAGE_NAME">
        $permissionTags
        <application />
      </manifest>
      """.trimIndent()
    )
  }
}
