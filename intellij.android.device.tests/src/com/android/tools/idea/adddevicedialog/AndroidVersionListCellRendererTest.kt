/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.intellij.ui.SimpleTextAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JList

@RunWith(JUnit4::class)
class AndroidVersionListCellRendererTest {
  private val renderer = AndroidVersionListCellRenderer()
  private val list = MockitoKt.mock<JList<AndroidVersion>>()

  @Test
  fun customizeCellRendererDetailsArentNull() {
    // Arrange
    val version = AndroidVersion(33, null, 4, false)

    // Act
    renderer.getListCellRendererComponent(list, version, 0, false, false)

    // Assert
    val i = renderer.iterator()

    i.next()
    assertEquals("API 33 ext. 4", i.fragment)
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, i.textAttributes)

    i.next()
    assertEquals(" \"Tiramisu\"; Android 13.0", i.fragment)
    assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, i.textAttributes)

    assertFalse(i.hasNext())
  }

  @Test
  fun customizeCellRenderer() {
    // Arrange
    val version = AndroidVersion(33, "UpsideDownCake", 5, true)

    // Act
    renderer.getListCellRendererComponent(list, version, 0, false, false)

    // Assert
    val i = renderer.iterator()

    i.next()
    assertEquals("API UpsideDownCake Preview", i.fragment)
    assertEquals(SimpleTextAttributes.REGULAR_ATTRIBUTES, i.textAttributes)

    assertFalse(i.hasNext())
  }
}
