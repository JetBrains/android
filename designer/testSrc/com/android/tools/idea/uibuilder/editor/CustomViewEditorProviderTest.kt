/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.flags.StudioFlags
import org.jetbrains.android.AndroidTestCase


class CustomViewEditorProviderTest : AndroidTestCase() {
  private lateinit var provider : CustomViewEditorProvider

  override fun setUp() {
    super.setUp()
    provider = CustomViewEditorProvider()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.clearOverride()
  }

  fun testDoNotAcceptKotlinWhenFlagDisabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(false)
    val file = myFixture.addFileToProject("src/SomeFile.kt", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptJavaWhenFlagDisabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(false)
    val file = myFixture.addFileToProject("src/SomeFile.java", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptResWhenFlagDisabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(false)
    val file = myFixture.addFileToProject("res/layout/some_layout.xml", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testAcceptKotlinWhenFlagEnabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(true)
    val file = myFixture.addFileToProject("src/SomeFile.kt", "")
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testAcceptJavaWhenFlagEnabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(true)
    val file = myFixture.addFileToProject("src/SomeFile.java", "")
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptResWhenFlagEnabled() {
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.override(true)
    val file = myFixture.addFileToProject("res/layout/some_layout.xml", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }
}