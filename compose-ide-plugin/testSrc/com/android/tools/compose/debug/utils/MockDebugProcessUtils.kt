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
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import com.sun.jdi.request.ClassPrepareRequest
import org.gradle.internal.impldep.org.eclipse.jgit.errors.NotSupportedException

interface MockDebugProcessScope {
  val virtualMachineProxy: VirtualMachineProxyImpl
  fun classType(
    signature: String,
    superClass: ClassType? = null,
    interfaces: List<InterfaceType> = emptyList(),
    block: MockReferenceTypeScope.() -> Unit = {}
  ): ReferenceType
}

interface MockReferenceTypeScope {
  fun method(
    name: String,
    signature: String? = null,
    argumentTypeNames: List<String> = emptyList(),
    lines: List<Int> = emptyList(),
    block: MockValueScope.() -> Unit = {}
  )
}

interface MockValueScope {
  fun value(value: Value)
}

fun mockDebugProcess(project: Project, block: MockDebugProcessScope.() -> Unit): MockDebugProcessImpl {
  val debugProcess = MockDebugProcessImpl(project)
  object : MockDebugProcessScope {
    override val virtualMachineProxy: VirtualMachineProxyImpl
      get() = debugProcess.virtualMachineProxy

    override fun classType(
      signature: String,
      superClass: ClassType?,
      interfaces: List<InterfaceType>,
      block: MockReferenceTypeScope.() -> Unit
    ): ClassType {
      val classType = debugProcess.addClassType(signature, superClass, interfaces) as MockClassType
      object : MockReferenceTypeScope {
        override fun method(
          name: String,
          signature: String?,
          argumentTypeNames: List<String>,
          lines: List<Int>,
          block: MockValueScope.() -> Unit
        ) {
          val method = MockMethod(name, signature, argumentTypeNames, lines, classType, debugProcess)
          classType.addMethod(method)

          object : MockValueScope {
            override fun value(value: Value) {
              classType.setValue(value, method)
            }
          }.block()
        }
      }.block()

      return classType
    }
  }.block()
  return debugProcess
}

class MockDebugProcessImpl(project: Project) : DebugProcessImpl(project) {
  private val referencesByName = mutableMapOf<String, ReferenceType>()
  private val mockVirtualMachineProxy = MockVirtualMachineProxy(this, referencesByName)

  val prepareRequestPatterns = mutableListOf<String>()
  private val mockRequestManager = object : RequestManagerImpl(this) {
    override fun createClassPrepareRequest(requestor: ClassPrepareRequestor, pattern: String): ClassPrepareRequest? {
      prepareRequestPatterns.add(pattern)
      return MockitoKt.mock()
    }
  }

  override fun getVirtualMachineProxy(): VirtualMachineProxyImpl = mockVirtualMachineProxy
  override fun getSearchScope(): GlobalSearchScope = GlobalSearchScope.allScope(project)
  override fun getRequestsManager(): RequestManagerImpl = mockRequestManager
  override fun isAttached() = true
  override fun invokeMethod(
    evaluationContext: EvaluationContext,
    objRef: ObjectReference,
    method: Method,
    args: List<Value>
  ): Value {
    val referenceType: ReferenceType = referencesByName[objRef.type().name()]
                                       ?: error("Reference type \"${objRef.type()}\" is not available when asked.")

    return when (referenceType) {
      is ClassType -> referenceType.invokeMethod(objRef.owningThread(), method, args, 0)
      else -> throw NotSupportedException("$referenceType is not supported yet.")
    }
  }

  override fun invokeInstanceMethod(
    evaluationContext: EvaluationContext,
    objRef: ObjectReference,
    method: Method,
    args: List<Value>,
    invocationOptions: Int
  ): Value {
    return invokeMethod(evaluationContext, objRef, method, args)
  }

  fun addClassType(
    name: String,
    superClass: ClassType?,
    interfaces: List<InterfaceType>
  ): ClassType {
    return MockClassType(this, name, superClass, interfaces).apply {
      referencesByName[name] = this
    }
  }
}
