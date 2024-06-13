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
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.InterfaceType
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine

sealed class MockReferenceType(
  private val debugProcess: DebugProcessImpl,
  private val name: String,
) : ReferenceType by MockitoKt.mock() {
  private val methods = mutableListOf<Method>()

  override fun methods(): List<Method> = methods

  override fun name() = name

  override fun signature(): String = "L$name;"

  override fun isPrepared(): Boolean = true

  override fun nestedTypes(): List<ReferenceType> =
    debugProcess.virtualMachineProxy.allClasses().filter { it.name().startsWith("${name()}\$") }

  override fun allLineLocations(): List<Location> = methods().flatMap { it.allLineLocations() }

  override fun allMethods(): List<Method> = methods()

  override fun methodsByName(name: String): List<Method> = methods().filter { it.name() == name }

  override fun virtualMachine(): VirtualMachine = debugProcess.virtualMachineProxy.virtualMachine

  override fun sourceName(): String {
    return "$name.kt"
  }

  fun addMethod(method: Method) {
    methods.add(method)
  }
}

class MockClassType(
  debugProcess: DebugProcessImpl,
  name: String,
  private val superClass: ClassType? = null,
  private val interfaces: List<InterfaceType> = emptyList(),
) : ClassType, MockReferenceType(debugProcess, name) {
  private val methodToValue = mutableMapOf<String, Value>()

  fun setValue(value: Value, method: Method) {
    val name = method.name() ?: error("Name of method \"$method\" is null.")
    methodToValue[name] = value
  }

  override fun invokeMethod(
    thread: ThreadReference?,
    method: Method,
    arguments: List<Value>,
    options: Int,
  ): Value {
    val name = method.name() ?: error("Name of method \"$method\" is null.")
    return methodToValue[name] ?: error("Fake value is not set for method \"$name\" when asked.")
  }

  override fun superclass(): ClassType? = superClass

  override fun interfaces(): List<InterfaceType> = interfaces

  override fun allInterfaces(): List<InterfaceType> = interfaces

  override fun subclasses(): List<ClassType> = emptyList()

  override fun isEnum() = false

  override fun setValue(field: Field?, value: Value?) {}

  override fun newInstance(
    thread: ThreadReference?,
    method: Method,
    arguments: List<Value>,
    options: Int,
  ): ObjectReference {
    throw UnsupportedOperationException()
  }

  override fun concreteMethodByName(name: String, signature: String?): Method? = null
}
