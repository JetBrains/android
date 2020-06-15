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
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.gradle.project.sync.setup.Facets
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class NavEditorProviderTest : AndroidTestCase() {

  private lateinit var provider : NavEditorProvider

  override fun setUp() {
    super.setUp()
    provider = NavEditorProvider()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testDoNotAcceptNonLayoutFile() {
    val file = myFixture.addFileToProject("src/SomeFile.kt", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptLayoutFile() {
    val file = myFixture.addFileToProject("res/layout/my_layout.xml", layoutContent())
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

  @Language("XML")
  private fun navigationContent(): String {
    val sb = StringBuilder()
    NavModelBuilderUtil.navigation("mynav").appendXml(sb, 0)
    return sb.toString()
  }
}
