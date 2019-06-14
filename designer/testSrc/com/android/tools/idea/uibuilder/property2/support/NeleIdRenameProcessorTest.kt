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
package com.android.tools.idea.uibuilder.property2.support

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeleIdRenameProcessorTest {

  @Test
  fun testTitle() {
    assertThat(NeleIdRenameProcessor.formatTitle("textView1", true, emptyList()))
      .isEqualTo("Update Usages of textView1")
    assertThat(NeleIdRenameProcessor.formatTitle("textView1", false, listOf("simple.xml")))
      .isEqualTo("Update Definitions of textView1")
    assertThat(NeleIdRenameProcessor.formatTitle("textView1", true, listOf("one.xml", "two.xml")))
      .isEqualTo("Update Usages and Definitions of textView1")
  }

  @Test
  fun testText() {
    assertThat(NeleIdRenameProcessor.formatText("textView1", true, emptyList())).isEqualTo("""
      Update all usages of textView1 as well?
      This will update all XML references and Java R field references.

      """.trimIndent())
    assertThat(NeleIdRenameProcessor.formatText("textView1", false, listOf("simple.xml"))).isEqualTo("""
      Update all other definitions of textView1 as well?
      It is defined here too: [simple.xml].

      """.trimIndent())
    assertThat(NeleIdRenameProcessor.formatText("textView1", true, listOf("one.xml", "two.xml"))).isEqualTo("""
      Update all usages of textView1 as well?
      This will update all XML references and Java R field references.

      Update all other definitions of textView1 as well?
      It is defined here too: [one.xml, two.xml].

      """.trimIndent())
  }
}