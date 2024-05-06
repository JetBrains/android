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
import com.sun.jdi.ClassObjectReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.Type
import com.sun.jdi.VirtualMachine

sealed class MockObjectReference(
  private val referenceType: ReferenceType,
  private val virtualMachine: VirtualMachine
) : ObjectReference by MockitoKt.mock() {
  override fun toString(): String {
    return "instance of " + referenceType().name()
  }

  override fun virtualMachine(): VirtualMachine = virtualMachine

  override fun type(): Type = referenceType

  override fun referenceType(): ReferenceType = referenceType
}

class MockClassObjectReference(
  private val referenceType: ReferenceType,
  private val virtualMachine: VirtualMachine
) : ClassObjectReference, MockObjectReference(referenceType, virtualMachine) {
  override fun reflectedType(): ReferenceType = referenceType

  override fun toString(): String {
    return "instance of " +
      referenceType().name() +
      "(reflected class=" +
      reflectedType().name() +
      ", " +
      "id=" +
      "@fakeUniqueId" +
      ")"
  }
}

class MockStringReference(
  private val value: String,
  referenceType: ReferenceType,
  vm: VirtualMachine
) : StringReference, MockObjectReference(referenceType, vm) {
  override fun value(): String = value

  override fun toString(): String = "\"$value\""
}
