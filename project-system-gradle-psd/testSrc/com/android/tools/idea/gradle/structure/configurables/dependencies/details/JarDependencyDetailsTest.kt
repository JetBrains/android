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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class JarDependencyDetailsTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testGetSupportedModelType() {
    val context = mock(PsContext::class.java)
    val details = JarDependencyDetails(context, showScope = false)
    assertThat(details.supportedModelType).isEqualTo(PsJarDependency::class.java)
  }

  @Test
  fun testGetModelAndContext() {
    val context = mock(PsContext::class.java)
    val details = JarDependencyDetails(context, showScope = false)
    assertThat(details.model).isNull()
    assertThat(details.context).isSameAs(context)
    assertThat(details.panel).isNotNull()
  }

  @Test
  fun testDisplay() {
    val context = mock(PsContext::class.java)
    val details = JarDependencyDetails(context, showScope = false)

    val dependency = mock(PsJarDependency::class.java)
    `when`(dependency.name).thenReturn("sample.jar")
    `when`(dependency.includes).thenReturn(listOf("*.jar"))
    `when`(dependency.excludes).thenReturn(listOf("excluded.jar"))

    details.display(dependency)
    assertThat(details.model).isSameAs(dependency)
  }
}
