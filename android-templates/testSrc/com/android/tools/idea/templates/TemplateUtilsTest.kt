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
package com.android.tools.idea.templates

import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase
import java.io.File

// TODO(qumeric): cover more functions

class TemplateUtilsTest : TestCase() {
  fun testHasExtension() {
    val ext = "sh"
    val fileWithExt = File("studio.$ext")
    val fileWithoutExt = File("studio")
    assertTrue(hasExtension(fileWithExt, ext))
    assertTrue(hasExtension(fileWithExt, ".$ext"))
    assertFalse(hasExtension(fileWithoutExt, ext))
  }

  fun testAppNameForTheme() {
    assertThat(getAppNameForTheme("My Application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("  My Application  withSpace  ")).isEqualTo("MyApplicationWithSpace")
    assertThat(getAppNameForTheme("my application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("My-Application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("1MyApplication")).isEqualTo("_1MyApplication")
    assertThat(getAppNameForTheme("--")).isEqualTo("App")
  }
}
