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
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.jdi.GeneratedLocation
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.request.ClassPrepareRequest

interface MockDebugProcessScope {
  fun type(signature: String, block: MockReferenceTypeScope.() -> Unit = {})
}

interface MockReferenceTypeScope {
  fun method(name: String, vararg lines: Int)
}

fun mockDebugProcess(project: Project, block: MockDebugProcessScope.() -> Unit): MockDebugProcessImpl {
  val debugProcess = MockDebugProcessImpl(project)
  object : MockDebugProcessScope {
    override fun type(signature: String, block: MockReferenceTypeScope.() -> Unit) {
      val methodToLines = mutableMapOf<String, List<Int>>()
      object : MockReferenceTypeScope {
        override fun method(name: String, vararg lines: Int) {
          methodToLines[name] = lines.toList()
        }
      }.block()
      debugProcess.addReferenceType(signature, methodToLines)
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

  fun addReferenceType(name: String, methodToLines: Map<String, List<Int>>) {
    referencesByName[name] = MockReferenceType(this, name, methodToLines)
  }
}

private class MockReferenceType(
  private val debugProcess: DebugProcessImpl,
  private val name: String,
  private val methodToLines: Map<String, List<Int>>
) : ReferenceType by MockitoKt.mock() {
  private val methods = methodToLines.keys.map { name ->
    val referenceType = this
    object : Method by MockitoKt.mock() {
      override fun name() = name
      override fun declaringType(): ReferenceType = referenceType
      override fun allLineLocations(): List<Location> =
        referenceType.allLineLocations().filter { it.method() == this }
    }
  }

  override fun name() = name
  override fun signature(): String = "L$name;"
  override fun isPrepared(): Boolean = true
  override fun nestedTypes(): List<ReferenceType> = debugProcess.virtualMachineProxy.allClasses().filter {
    it.name().startsWith("${name()}\$")
  }

  override fun allLineLocations(): List<Location> = methodToLines.flatMap { (name, lines) ->
    lines.map { line -> GeneratedLocation(debugProcess, this, name, line) }
  }

  override fun allMethods(): List<Method> = methods
  override fun methodsByName(name: String): List<Method> = methods.filter { it.name() == name }
}

private class MockVirtualMachine(
  private val referencesByName: Map<String, ReferenceType>
) : VirtualMachine by MockitoKt.mock() {
  override fun name(): String = "MockDalvik"
  override fun allClasses(): List<ReferenceType> = referencesByName.values.toList()
  override fun classesByName(s: String): List<ReferenceType> = listOfNotNull(referencesByName[s])
}

private class MockVirtualMachineProxy(
  debugProcessImpl: DebugProcessImpl,
  referencesByName: Map<String, ReferenceType>
) : VirtualMachineProxyImpl(debugProcessImpl, MockVirtualMachine(referencesByName)) {
  override fun allClasses(): List<ReferenceType> = virtualMachine.allClasses()
  override fun classesByName(s: String): List<ReferenceType> = virtualMachine.classesByName(s)
}
