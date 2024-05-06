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
import com.intellij.util.lang.JavaVersion
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

// TODO(qumeric): cover more functions

@RunWith(JUnit4::class)
class TemplateUtilsTest {
  @Test
  fun hasExtension() {
    val ext = "sh"
    val fileWithExt = File("studio.$ext")
    val fileWithoutExt = File("studio")
    assertTrue(hasExtension(fileWithExt, ext))
    assertTrue(hasExtension(fileWithExt, ".$ext"))
    assertFalse(hasExtension(fileWithoutExt, ext))
  }

  @Test
  fun appNameForTheme() {
    assertThat(getAppNameForTheme("My Application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("  My Application  withSpace  "))
      .isEqualTo("MyApplicationWithSpace")
    assertThat(getAppNameForTheme("my application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("My-Application")).isEqualTo("MyApplication")
    assertThat(getAppNameForTheme("1MyApplication")).isEqualTo("_1MyApplication")
    assertThat(getAppNameForTheme("--")).isEqualTo("App")
  }

  @Test
  fun convertJavaVersionToGradleString() {
    Assert.assertEquals(
      "JavaVersion.VERSION_1_7",
      TemplateUtils.convertJavaVersionToGradleString(JavaVersion.parse("1.7.0"))
    )

    Assert.assertEquals(
      "JavaVersion.VERSION_1_8",
      TemplateUtils.convertJavaVersionToGradleString(JavaVersion.parse("1.8.0_392"))
    )

    Assert.assertEquals(
      "JavaVersion.VERSION_17",
      TemplateUtils.convertJavaVersionToGradleString(JavaVersion.parse("17.0.8"))
    )

    Assert.assertEquals(
      "JavaVersion.VERSION_18",
      TemplateUtils.convertJavaVersionToGradleString(JavaVersion.parse("18.0.2"))
    )
  }
}
