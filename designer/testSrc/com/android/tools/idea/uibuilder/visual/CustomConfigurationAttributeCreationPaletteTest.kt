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
import java.awt.event.ActionEvent
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel
import org.mockito.Mockito

class CustomConfigurationAttributeCreationPaletteTest : LayoutTestCase() {
  override fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
    super.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  fun testCreateConfiguration() {
    val file = myFixture.addFileToProject("/res/layout/test.xml", LAYOUT_FILE_CONTENT)

    // Temp class for Mockito to verify callback.
    open class MyConsumer : Consumer<String> {
      override fun accept(t: String) = Unit
    }
    val mockedConsumer = Mockito.mock(MyConsumer::class.java)

    val palette =
      CustomConfigurationAttributeCreationPalette(file.virtualFile, myModule) {
        mockedConsumer.accept(it.name)
      }

    val addButton =
      (palette.components[2] as JPanel).components.filterIsInstance<JButton>().first {
        it.text == "Add"
      }
    addButton.action.actionPerformed(Mockito.mock(ActionEvent::class.java))
    Mockito.verify(mockedConsumer).accept("Preview")
  }
}

private const val LAYOUT_FILE_CONTENT =
  """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="match_parent"
  android:layout_height="match_parent"
  android:orientation="vertical">
</LinearLayout>
"""
