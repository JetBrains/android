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
package com.android.tools.rendering.parsers

import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XmlParserTest {
  @Test
  fun test() {
    @Language("XML")
    val layoutString =
      """
      <LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:orientation="vertical">
        <Button
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:text="Click me"/>
        <TextView
          android:layout_width="wrap_content"
          android:layout_height="wrap_content" />
      </LinearLayout>"""

    val rootTag = parseRootTag(layoutString)
    assertEquals("LinearLayout", rootTag.name)
    val rootAttr = rootTag.getAttribute("orientation", "http://schemas.android.com/apk/res/android")
    assertNotNull(rootAttr)
    assertEquals("vertical", rootAttr!!.value)
    val button = rootTag.subTags.find { it.name == "Button" }
    assertNotNull(button)
    assertEquals(
      "wrap_content",
      button!!.getAttribute("layout_width", "http://schemas.android.com/apk/res/android")!!.value,
    )
    assertEquals(
      "Click me",
      button.getAttribute("text", "http://schemas.android.com/apk/res/android")!!.value,
    )
    val textView = rootTag.subTags.find { it.name == "TextView" }
    assertNotNull(textView)
  }
}
