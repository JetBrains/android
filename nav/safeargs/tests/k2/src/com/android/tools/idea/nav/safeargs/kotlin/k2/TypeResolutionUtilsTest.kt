/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypeResolutionUtilsTest {
  private data class NavArgumentDataImpl(
    override val name: String,
    override val type: String? = null,
    override val defaultValue: String? = null,
    override val nullable: String? = null,
  ) : NavArgumentData

  private fun resolve(
    type: String? = null,
    defaultValue: String? = null,
    nullable: String? = null,
  ): String =
    NavArgumentDataImpl("arg", type, defaultValue, nullable).resolveKotlinType(PACKAGE_NAME)

  @Test
  fun primitives_explicit() {
    assertThat(resolve(type = "string")).isEqualTo("kotlin.String")
    assertThat(resolve(type = "integer")).isEqualTo("kotlin.Int")
    assertThat(resolve(type = "reference")).isEqualTo("kotlin.Int")
    assertThat(resolve(type = "long")).isEqualTo("kotlin.Long")
    assertThat(resolve(type = "float")).isEqualTo("kotlin.Float")
    assertThat(resolve(type = "boolean")).isEqualTo("kotlin.Boolean")
  }

  @Test
  fun primitives_inferredType() {
    assertThat(resolve(defaultValue = "foo")).isEqualTo("kotlin.String")
    assertThat(resolve(defaultValue = "42")).isEqualTo("kotlin.Int")
    assertThat(resolve(defaultValue = "42.0")).isEqualTo("kotlin.Float")
    assertThat(resolve(defaultValue = "true")).isEqualTo("kotlin.Boolean")
    assertThat(resolve(defaultValue = "@+id/foo")).isEqualTo("kotlin.Int")
  }

  @Test
  fun fallback_inferredType() {
    assertThat(resolve(defaultValue = null)).isEqualTo("kotlin.String")
    assertThat(resolve(defaultValue = "@null")).isEqualTo("kotlin.String?")
  }

  @Test
  fun primitiveArrays() {
    assertThat(resolve(type = "string[]")).isEqualTo("kotlin.Array<kotlin.String>")
    assertThat(resolve(type = "integer[]")).isEqualTo("kotlin.IntArray")
    assertThat(resolve(type = "reference[]")).isEqualTo("kotlin.IntArray")
    assertThat(resolve(type = "long[]")).isEqualTo("kotlin.LongArray")
    assertThat(resolve(type = "float[]")).isEqualTo("kotlin.FloatArray")
    assertThat(resolve(type = "boolean[]")).isEqualTo("kotlin.BooleanArray")
  }

  @Test
  fun nullables() {
    assertThat(resolve(type = "reference", nullable = "true")).isEqualTo("kotlin.Int?")
    assertThat(resolve(type = "reference[]", nullable = "true")).isEqualTo("kotlin.IntArray?")
    assertThat(resolve(type = "reference", defaultValue = "@null")).isEqualTo("kotlin.Int?")
  }

  @Test
  fun userTypes() {
    assertThat(resolve(type = ".Baz")).isEqualTo("foo.bar.Baz")
    assertThat(resolve(type = "quux.Baz")).isEqualTo("quux.Baz")
    assertThat(resolve(type = ".Baz[]")).isEqualTo("kotlin.Array<foo.bar.Baz>")
    assertThat(resolve(type = ".Baz", nullable = "true")).isEqualTo("foo.bar.Baz?")
    assertThat(resolve(type = ".Baz", defaultValue = "@null")).isEqualTo("foo.bar.Baz?")
  }

  companion object {
    private const val PACKAGE_NAME = "foo.bar"
  }
}
