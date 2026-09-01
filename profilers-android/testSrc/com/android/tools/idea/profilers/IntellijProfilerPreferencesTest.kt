/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class IntellijProfilerPreferencesTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testStringValue() {
    val preferences = IntellijProfilerPreferences()
    val properties = PropertiesComponent.getInstance()

    assertThat(preferences.getValue(".test.string.key", "default_val")).isEqualTo("default_val")

    preferences.setValue(".test.string.key", "custom_val")
    assertThat(preferences.getValue(".test.string.key", "default_val")).isEqualTo("custom_val")
    assertThat(properties.getValue("studio.profiler.test.string.key")).isEqualTo("custom_val")
  }

  @Test
  fun testFloatValue() {
    val preferences = IntellijProfilerPreferences()
    val properties = PropertiesComponent.getInstance()

    assertThat(preferences.getFloat(".test.float.key", 1.5f)).isEqualTo(1.5f)

    preferences.setFloat(".test.float.key", 3.14f)
    assertThat(preferences.getFloat(".test.float.key", 0.0f)).isEqualTo(3.14f)
    assertThat(properties.getFloat("studio.profiler.test.float.key", 0.0f)).isEqualTo(3.14f)

    preferences.setFloat(".test.float.key.custom", 2.71f, 1.0f)
    assertThat(preferences.getFloat(".test.float.key.custom", 0.0f)).isEqualTo(2.71f)
  }

  @Test
  fun testIntValue() {
    val preferences = IntellijProfilerPreferences()
    val properties = PropertiesComponent.getInstance()

    assertThat(preferences.getInt(".test.int.key", 42)).isEqualTo(42)

    preferences.setInt(".test.int.key", 100)
    assertThat(preferences.getInt(".test.int.key", 0)).isEqualTo(100)
    assertThat(properties.getInt("studio.profiler.test.int.key", 0)).isEqualTo(100)

    preferences.setInt(".test.int.key.custom", 200, 50)
    assertThat(preferences.getInt(".test.int.key.custom", 0)).isEqualTo(200)
  }

  @Test
  fun testBooleanValue() {
    val preferences = IntellijProfilerPreferences()
    val properties = PropertiesComponent.getInstance()

    assertThat(preferences.getBoolean(".test.bool.key", true)).isTrue()
    assertThat(preferences.getBoolean(".test.bool.key", false)).isFalse()

    preferences.setBoolean(".test.bool.key", true)
    assertThat(preferences.getBoolean(".test.bool.key", false)).isTrue()
    assertThat(properties.getBoolean("studio.profiler.test.bool.key")).isTrue()

    preferences.setBoolean(".test.bool.key", false)
    assertThat(preferences.getBoolean(".test.bool.key", false)).isFalse()
    assertThat(properties.getBoolean("studio.profiler.test.bool.key")).isFalse()
  }

  @Test
  fun testGetProfilerPropertyName() {
    assertThat(IntellijProfilerPreferences.getProfilerPropertyName("my.key")).isEqualTo("studio.profilermy.key")
    assertThat(IntellijProfilerPreferences.getProfilerPropertyName(".my.key")).isEqualTo("studio.profiler.my.key")
  }
}
