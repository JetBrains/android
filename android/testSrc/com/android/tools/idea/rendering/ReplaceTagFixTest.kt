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
package com.android.tools.idea.rendering

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.xml.XmlFile
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReplaceTagFixTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testTagReplace() {
    @Language("XML")
    val layoutContents = """
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="fill_parent"
    android:layout_height="wrap_content">
    <TextViw
        android:layout_width="fill_parent"
        android:layout_height="wrap_content">
    </TextViw>

    <LinearLayout
    android:layout_width="fill_parent"
    android:layout_height="wrap_content">
     <TextView
          android:layout_width="fill_parent"
          android:layout_height="wrap_content"
          android:text="TextViw"
      />
  </LinearLayout>
</LinearLayout>
    """.trimIndent()
    val xmlFile = projectRule.fixture.addFileToProject("src/res/layout/test.xml", layoutContents) as XmlFile

    ApplicationManager.getApplication().invokeAndWait {
      ReplaceTagFix(xmlFile, "TextViw", "TextView").run()
    }

    val afterText = ReadAction.compute<String, Throwable> {
      xmlFile.document?.text
    }!!

    val matches = Regex("TextViw").findAll(afterText)
      .map { it.value }
      .toList()

    assertEquals(1, matches.size)
  }
}