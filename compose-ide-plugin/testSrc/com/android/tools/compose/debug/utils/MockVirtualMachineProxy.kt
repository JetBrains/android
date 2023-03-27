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
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadGroupReference
import com.sun.jdi.VirtualMachine

class MockVirtualMachineProxy(
  debugProcessImpl: DebugProcessImpl,
  referencesByName: Map<String, ReferenceType>
) : VirtualMachineProxyImpl(debugProcessImpl, MockVirtualMachine(referencesByName)) {
  override fun allClasses(): List<ReferenceType> = virtualMachine.allClasses()
  override fun classesByName(s: String): List<ReferenceType> = virtualMachine.classesByName(s)
}

private class MockVirtualMachine(
  private val referencesByName: Map<String, ReferenceType>
) : VirtualMachine by MockitoKt.mock() {
  override fun name(): String = "MockDalvik"
  override fun allClasses(): List<ReferenceType> = referencesByName.values.toList()
  override fun classesByName(s: String): List<ReferenceType> = listOfNotNull(referencesByName[s])
  override fun topLevelThreadGroups(): List<ThreadGroupReference> = emptyList()
}