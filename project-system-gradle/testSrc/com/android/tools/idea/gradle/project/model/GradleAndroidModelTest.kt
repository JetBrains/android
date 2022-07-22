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
package com.android.tools.idea.gradle.project.model

import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GradleAndroidModelTest {

  @Test
  fun testClassFieldsToDynamicResourceValues() {
    val input = mapOf(
      "foo" to IdeClassFieldImpl(type = ResourceType.STRING.getName(), name = "foo", value = "baz"),
      "foo2" to IdeClassFieldImpl(type = ResourceType.INTEGER.getName(), name = "foo2", value = "123")
    )
    val output = classFieldsToDynamicResourceValues(input)

    val expectedOutput = mapOf(
      "foo" to DynamicResourceValue(ResourceType.STRING, "baz"),
      "foo2" to DynamicResourceValue(ResourceType.INTEGER, "123")
    )

    assertThat(output).isEqualTo(expectedOutput)
  }
}