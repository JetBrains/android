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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

@RunWith(JUnit4::class)
class GradleAndroidModelDataTest {

  @Test
  fun `ensure pure data class`() {
    val klass = GradleAndroidModelData::class
    val nonDataClassPropertyNames = getNonDataClassPropertyNames(klass)
    // GradleAndroidModelData is supposed to be a pure data class (i.e. no other fields than supporting data class properties).
    assertThat(nonDataClassPropertyNames).isEmpty()
  }

  private fun getNonDataClassPropertyNames(klass: KClass<GradleAndroidModelData>): Set<String?> {
    val constructorProperties = klass.primaryConstructor?.parameters?.map { it.name }?.toSet().orEmpty()
    val allFieldBackedProperties = klass.memberProperties.filter { it.javaField != null }.map { it.name }.toSet()
    return allFieldBackedProperties - constructorProperties
  }
}