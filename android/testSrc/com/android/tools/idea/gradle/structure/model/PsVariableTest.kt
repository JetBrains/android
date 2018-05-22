/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock


class PsVariableTest {

  @Test
  fun testSetValue() {
    val stringProperty = mock(GradlePropertyModel::class.java)
    val stringPropertyResolved = mock(ResolvedPropertyModel::class.java)
    Mockito.`when`(stringProperty.valueType).thenReturn(GradlePropertyModel.ValueType.STRING)
    val testStringVariable = PsVariable(stringProperty, stringPropertyResolved, mock(PsModel::class.java), mock(PsVariablesScope::class.java))
    testStringVariable.setValue("true")
    Mockito.verify(stringProperty).setValue("true")

    val booleanProperty = mock(GradlePropertyModel::class.java)
    val booleanPropertyResolved = mock(ResolvedPropertyModel::class.java)
    Mockito.`when`(booleanProperty.valueType).thenReturn(GradlePropertyModel.ValueType.BOOLEAN)
    val testBooleanVariable = PsVariable(booleanProperty, booleanPropertyResolved, mock(PsModel::class.java), mock(PsVariablesScope::class.java))
    testBooleanVariable.setValue("true")
    Mockito.verify(booleanProperty).setValue(true)
  }
}
