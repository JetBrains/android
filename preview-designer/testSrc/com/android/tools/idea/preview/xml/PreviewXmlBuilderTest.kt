/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.xml

import org.junit.Test
import kotlin.test.assertEquals

class PreviewXmlBuilderTest {
  @Test
  fun testGeneratedXml() {
    val builder = PreviewXmlBuilder("com.foo.bar.CustomView")
    builder
      .toolsAttribute("baz", "hello")
      .androidAttribute("intFoo", "1")
    assertEquals(
      //language=XML
      """
        <com.foo.bar.CustomView
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools"
            tools:baz="hello"
            android:intFoo="1" />
      """.trimIndent(),
      builder.buildString().trimIndent())
  }

}