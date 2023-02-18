// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.profilers.memory

/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.profilers.memory.ValueColumnRenderer.Companion.getValueObjectIcon
import com.android.tools.profilers.memory.adapters.FieldObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.ReferenceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons.Debugger.*
import com.intellij.icons.AllIcons.Hierarchy.Subtypes
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.PlatformIcons.INTERFACE_ICON
import icons.StudioIcons.Profiler.Overlays.*
import org.junit.Test
import javax.swing.Icon

class ValueColumnRendererTest {

  @Test
  fun `value object has the right icon`() {
    fun makeInst(type: ValueType, callStackDepth: Int, isRoot: Boolean = false) = object : InstanceObject {
      override fun getName() = "inst"
      override fun getClassEntry() = throw NotImplementedError()
      override fun getValueType() = type
      override fun getHeapId() = throw NotImplementedError()
      override fun getCallStackDepth() = callStackDepth
      override fun getIsRoot() = isRoot
    }
    fun makeField(type: ValueType, callStackDepth: Int?) = object : FieldObject {
      override fun getName() = "field"
      override fun getAsInstance() = callStackDepth?.let { makeInst(type, it) }
      override fun getValueType() = type
      override fun getValue() = null
      override fun getFieldName() = "field"
    }
    fun makeRef(refInst: InstanceObject) = ReferenceObject(listOf("ref1", "ref2"), refInst)
    fun check(obj: ValueObject, icon: Icon) = assertThat(obj.getValueObjectIcon()).isEqualTo(icon)

    check(makeField(ValueType.ARRAY, null), Db_array)
    check(makeField(ValueType.ARRAY, 1), ARRAY_STACK)
    check(makeField(ValueType.INT, null), Db_primitive)
    check(makeField(ValueType.OBJECT, 0), IconManager.getInstance().getPlatformIcon(PlatformIcons.Field))
    check(makeField(ValueType.STRING, 1), FIELD_STACK)

    check(makeRef(makeInst(ValueType.OBJECT, 0, true)), Subtypes)
    check(makeRef(makeInst(ValueType.ARRAY, 0)), Db_array)
    check(makeRef(makeInst(ValueType.ARRAY, 1)), ARRAY_STACK)
    check(makeRef(makeInst(ValueType.OBJECT, 0)), IconManager.getInstance().getPlatformIcon(PlatformIcons.Field))
    check(makeRef(makeInst(ValueType.OBJECT, 1)), FIELD_STACK)

    check(makeInst(ValueType.STRING, 0), INTERFACE_ICON)
    check(makeInst(ValueType.INT, 1), INTERFACE_STACK)

    check(object : ValueObject {
      override fun getName() = "obj"
      override fun getValueType() = ValueType.OBJECT
    }, INTERFACE_ICON)
  }
}