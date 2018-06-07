/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.simpleperf

import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.SyscallModel
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class NodeNameParserTest {

  @Test
  fun testCppMethodsParsing() {
    var model = NodeNameParser.parseNodeName("art::ArtMethod::Invoke()")
    assertThat(model).isInstanceOf(CppFunctionModel::class.java)
    assertThat(model.name).isEqualTo("Invoke")
    assertThat(model.fullName).isEqualTo("art::ArtMethod::Invoke")
    assertThat(model.id).isEqualTo("art::ArtMethod::Invoke[]")
    var cppFunctionModel: CppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::ArtMethod")
    assertThat(cppFunctionModel.parameters).isEmpty()

    model = NodeNameParser.parseNodeName("art::interpreter::DoCall(bool, art::Thread*)")
    assertThat(model.name).isEqualTo("DoCall")
    assertThat(model.fullName).isEqualTo("art::interpreter::DoCall")
    assertThat(model.id).isEqualTo("art::interpreter::DoCall[bool, art::Thread*]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::interpreter")
    assertThat(cppFunctionModel.parameters).hasSize(2)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")
    assertThat(cppFunctionModel.parameters[1]).isEqualTo("art::Thread*")

    model = NodeNameParser.parseNodeName("art::SomeClass::add<int>()")
    assertThat(model.name).isEqualTo("add")
    assertThat(model.fullName).isEqualTo("art::SomeClass::add")
    assertThat(model.id).isEqualTo("art::SomeClass::add[]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::SomeClass")
    assertThat(cppFunctionModel.parameters).isEmpty()

    model = NodeNameParser.parseNodeName("Shader::Render(glm::detail::tmat4x4<float, (glm::precision)0>*)")
    assertThat(model.name).isEqualTo("Render")
    assertThat(model.fullName).isEqualTo("Shader::Render")
    assertThat(model.id).isEqualTo("Shader::Render[glm::detail::tmat4x4*]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("Shader")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("glm::detail::tmat4x4*")

    model = NodeNameParser.parseNodeName("art::StackVisitor::GetDexPc(bool) const")
    assertThat(model.name).isEqualTo("GetDexPc")
    assertThat(model.fullName).isEqualTo("art::StackVisitor::GetDexPc")
    assertThat(model.id).isEqualTo("art::StackVisitor::GetDexPc[bool]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::StackVisitor")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")

    model = NodeNameParser.parseNodeName("Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)")
    assertThat(model.name).isEqualTo("FuncTemplate")
    assertThat(model.fullName).isEqualTo("Type2::FuncTemplate")
    assertThat(model.id).isEqualTo("Type2::FuncTemplate[Type4]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("Type2")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("Type4")

    model = NodeNameParser.parseNodeName("int Abc123(bool)")
    assertThat(model.name).isEqualTo("Abc123")
    assertThat(model.fullName).isEqualTo("Abc123")
    assertThat(model.id).isEqualTo("Abc123[bool]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")

    try {
      NodeNameParser.parseNodeName("malformed::method<(")
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Native function signature must have matching parentheses and brackets.")
    }
  }

  @Test
  fun testJavaMethodsParsing() {
    var model = NodeNameParser.parseNodeName("java.util.String.toString")
    assertThat(model).isInstanceOf(JavaMethodModel::class.java)
    assertThat(model.name).isEqualTo("toString")
    assertThat(model.fullName).isEqualTo("java.util.String.toString")
    assertThat(model.id).isEqualTo("java.util.String.toString")
    var javaMethodModel: JavaMethodModel = model as JavaMethodModel
    assertThat(javaMethodModel.className).isEqualTo("java.util.String")
    assertThat(javaMethodModel.signature).isEmpty()

    model = NodeNameParser.parseNodeName("java.lang.Object.internalClone [DEDUPED]")
    assertThat(model).isInstanceOf(JavaMethodModel::class.java)
    assertThat(model.name).isEqualTo("internalClone [DEDUPED]")
    assertThat(model.fullName).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
    assertThat(model.id).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
    javaMethodModel = model as JavaMethodModel
    assertThat(javaMethodModel.className).isEqualTo("java.lang.Object")
    assertThat(javaMethodModel.signature).isEmpty()
  }

  @Test
  fun testSyscallParsing() {
    val model = NodeNameParser.parseNodeName("write")
    assertThat(model).isInstanceOf(SyscallModel::class.java)
    assertThat(model.name).isEqualTo("write")
    assertThat(model.fullName).isEqualTo("write")
    assertThat(model.id).isEqualTo("write")
  }
}
