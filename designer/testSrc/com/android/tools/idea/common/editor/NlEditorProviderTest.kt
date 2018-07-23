/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.gradle.project.sync.setup.Facets
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class NlEditorProviderTest : AndroidTestCase() {

  private lateinit var provider : NlEditorProvider

  override fun setUp() {
    super.setUp()
    provider = NlEditorProvider()
  }

  fun testDoNotAcceptNonLayoutFile() {
    val file = myFixture.addFileToProject("src/SomeFile.kt", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testAcceptLayoutFileInAndroidModuleOnly() {
    val file = myFixture.addFileToProject("res/layout/my_layout.xml", layoutContent())
    assertTrue(provider.accept(project, file.virtualFile))

    // Makes the module is not an AndroidModule by removing AndroidFacet from it.
    val modifiableModel = FacetManager.getInstance(myModule).createModifiableModel()
    Facets.removeAllFacets(modifiableModel, AndroidFacet.ID)
    ApplicationManager.getApplication().runWriteAction {
      modifiableModel.commit()
    }
    assertFalse(provider.accept(project, file.virtualFile))
  }

  @Language("XML")
  private fun layoutContent(): String {
    val layout = ComponentDescriptor(SdkConstants.LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
    val sb = StringBuilder(1000)
    layout.appendXml(sb, 0)
    return sb.toString()
  }
}
