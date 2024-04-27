/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.debug.utils

import com.android.testutils.MockitoKt
import com.sun.jdi.IntegerType
import com.sun.jdi.IntegerValue
import com.sun.jdi.Type
import com.sun.jdi.VirtualMachine

class MockIntegerValue(private val value: Int, private val virtualMachine: VirtualMachine) :
  IntegerValue by MockitoKt.mock() {
  override fun toString(): String = value.toString()

  override fun virtualMachine(): VirtualMachine = virtualMachine

  override fun type(): Type = MockitoKt.mock<IntegerType>()

  override fun intValue(): Int = value

  override fun longValue(): Long = value.toLong()

  override fun value(): Int = value
}
