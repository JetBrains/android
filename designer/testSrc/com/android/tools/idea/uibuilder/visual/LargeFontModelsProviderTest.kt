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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.type.LayoutFileType
import org.intellij.lang.annotations.Language
import org.mockito.Mockito

class LargeFontModelsProviderTest : LayoutTestCase() {

  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    super.setUp()
  }

  fun testCreateModels() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    val modelsProvider = LargeFontModelsProvider
    val nlModels = modelsProvider.createNlModels(testRootDisposable, file, myFacet)

    assertEquals(1.0f, nlModels[0].configuration.fontScale)
    assertEquals(0.85f, nlModels[1].configuration.fontScale)
    assertEquals(1.15f, nlModels[2].configuration.fontScale)
    assertEquals(1.3f, nlModels[3].configuration.fontScale)
  }
}

@Language("Xml")
private const val LAYOUT_FILE_CONTENT = """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""