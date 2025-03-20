/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleVariableTest {

  @Test
  fun applyTo() {
    val variable = RuleVariable("Name", "Foo")

    assertThat(variable.applyTo("\${Name}")).isEqualTo("Foo")
  }

  @Test
  fun applyTo_embedded() {
    val variable = RuleVariable("NAME", "Foo")

    assertThat(variable.applyTo("My name is \${NAME}!")).isEqualTo("My name is Foo!")
  }

  @Test
  fun applyTo_multipleOccurrences() {
    val variable = RuleVariable("NAME", "Foo")

    assertThat(variable.applyTo("\${NAME} \${NAME} \${NAME}")).isEqualTo("Foo Foo Foo")
  }

  @Test
  fun applyTo_list() {
    val variables = listOf(RuleVariable("NAME", "Foo"), RuleVariable("AGE", "10"))

    assertThat(variables.applyTo("My name is \${NAME} and I'm \${AGE} years old"))
      .isEqualTo("My name is Foo and I'm 10 years old")
  }

  @Test
  fun applyTo_list_null() {
    val variables = listOf(RuleVariable("NAME", "Foo"), RuleVariable("AGE", "10"))

    assertThat(variables.applyTo(null)).isNull()
  }
}
