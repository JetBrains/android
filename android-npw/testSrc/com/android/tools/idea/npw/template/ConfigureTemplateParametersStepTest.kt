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
package com.android.tools.idea.npw.template

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.StringParameter
import com.intellij.openapi.util.Disposer
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConfigureTemplateParametersStepTest {
  private lateinit var step: ConfigureTemplateParametersStep

  @Before
  fun mockStep() {
    step = ConfigureTemplateParametersStep(mock(), "", mock())
  }

  @After
  fun dispose() {
    Disposer.dispose(step)
  }

  @Test
  fun packageConstraintsAreValidatedBeforeClass() {
    val param1 = mock<StringParameter>() // e.g. Activity Name
    whenever(param1.isVisibleAndEnabled).thenReturn(true)
    whenever(param1.constraints).thenReturn(listOf(CLASS, UNIQUE, NONEMPTY))
    val param2 = mock<StringParameter>() // e.g. Layout Name when generate layout is unchecked
    whenever(param2.isVisibleAndEnabled).thenReturn(false)
    whenever(param2.constraints).thenReturn(listOf(LAYOUT, UNIQUE, NONEMPTY))
    val param3 = mock<StringParameter>() // e.g. Launcher Activity
    whenever(param3.isVisibleAndEnabled).thenReturn(true)
    whenever(param3.constraints).thenReturn(listOf())
    val param4 = mock<StringParameter>() // e.g. Package Name
    whenever(param4.isVisibleAndEnabled).thenReturn(true)
    whenever(param4.constraints).thenReturn(listOf(PACKAGE))

    val actual = step.getSortedStringParametersForValidation(listOf(param1, param2, param3, param4))

    assertEquals(listOf(param4, param1, param3), actual)
  }
}
