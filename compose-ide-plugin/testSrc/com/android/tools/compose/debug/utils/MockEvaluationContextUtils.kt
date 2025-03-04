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

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.sun.jdi.ObjectReference
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun mockEvaluationContext(
  debugProcess: DebugProcessImpl,
  objectReference: ObjectReference,
): EvaluationContextImpl {
  val mockSuspendContext = mock<SuspendContextImpl>()
  whenever(mockSuspendContext.debugProcess).thenReturn(debugProcess)
  whenever(mockSuspendContext.virtualMachineProxy).thenReturn(mock<VirtualMachineProxyImpl>())
  whenever(mockSuspendContext.managerThread).thenReturn(debugProcess.managerThread)

  val mockFrameProxyImpl = mock<StackFrameProxyImpl>()
  whenever(mockFrameProxyImpl.thisObject()).thenReturn(objectReference)

  return EvaluationContextImpl(mockSuspendContext, mockFrameProxyImpl)
}
