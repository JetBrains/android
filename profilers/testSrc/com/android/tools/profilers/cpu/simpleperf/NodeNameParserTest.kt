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
import org.junit.Test

class NodeNameParserTest {

  @Test
  fun `test cpp methods parsing`() {
    var model = NodeNameParser.parseNodeName("art::ArtMethod::Invoke()", true)
    assertThat(model).isInstanceOf(CppFunctionModel::class.java)
    assertThat(model.name).isEqualTo("Invoke")
    assertThat(model.fullName).isEqualTo("art::ArtMethod::Invoke")
    assertThat(model.id).isEqualTo("art::ArtMethod::Invoke[]")
    var cppFunctionModel: CppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::ArtMethod")
    assertThat(cppFunctionModel.parameters).isEmpty()
    assertThat(cppFunctionModel.isUserCode).isTrue()

    model = NodeNameParser.parseNodeName("art::interpreter::DoCall(bool, art::Thread*)", false)
    assertThat(model.name).isEqualTo("DoCall")
    assertThat(model.fullName).isEqualTo("art::interpreter::DoCall")
    assertThat(model.id).isEqualTo("art::interpreter::DoCall[bool, art::Thread*]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::interpreter")
    assertThat(cppFunctionModel.parameters).hasSize(2)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")
    assertThat(cppFunctionModel.parameters[1]).isEqualTo("art::Thread*")
    assertThat(cppFunctionModel.isUserCode).isFalse()

    model = NodeNameParser.parseNodeName("art::SomeClass::add<int>()", true)
    assertThat(model.name).isEqualTo("add")
    assertThat(model.fullName).isEqualTo("art::SomeClass::add")
    assertThat(model.id).isEqualTo("art::SomeClass::add[]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::SomeClass")
    assertThat(cppFunctionModel.parameters).isEmpty()
    assertThat(cppFunctionModel.isUserCode).isTrue()

    model = NodeNameParser.parseNodeName("Shader::Render(glm::detail::tmat4x4<float, (glm::precision)0>*)", false)
    assertThat(model.name).isEqualTo("Render")
    assertThat(model.fullName).isEqualTo("Shader::Render")
    assertThat(model.id).isEqualTo("Shader::Render[glm::detail::tmat4x4*]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("Shader")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("glm::detail::tmat4x4*")
    assertThat(cppFunctionModel.isUserCode).isFalse()

    model = NodeNameParser.parseNodeName("art::StackVisitor::GetDexPc(bool) const", true)
    assertThat(model.name).isEqualTo("GetDexPc")
    assertThat(model.fullName).isEqualTo("art::StackVisitor::GetDexPc")
    assertThat(model.id).isEqualTo("art::StackVisitor::GetDexPc[bool]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("art::StackVisitor")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")
    assertThat(cppFunctionModel.isUserCode).isTrue()

    model = NodeNameParser.parseNodeName("Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)", false)
    assertThat(model.name).isEqualTo("FuncTemplate")
    assertThat(model.fullName).isEqualTo("Type2::FuncTemplate")
    assertThat(model.id).isEqualTo("Type2::FuncTemplate[Type4]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("Type2")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("Type4")
    assertThat(cppFunctionModel.isUserCode).isFalse()

    model = NodeNameParser.parseNodeName("int Abc123(bool)", true)
    assertThat(model.name).isEqualTo("Abc123")
    assertThat(model.fullName).isEqualTo("Abc123")
    assertThat(model.id).isEqualTo("Abc123[bool]")
    cppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("")
    assertThat(cppFunctionModel.parameters).hasSize(1)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("bool")
  }

  @Test
  fun `un-matching parenthesis shouldn't throw exceptions`() {
    val model = NodeNameParser.parseNodeName("malformed::method(a))", true)
    assertThat(model).isInstanceOf(CppFunctionModel::class.java)
    // Instead of throwing an exception when finding matching parenthesis, we include all the parenthesis in the method name.
    assertThat(model.name).isEqualTo("method(a))")
    assertThat(model.fullName).isEqualTo("malformed::method(a))")
    assertThat(model.id).isEqualTo("malformed::method(a))[]")
    val cppFunctionModel: CppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("malformed")
    assertThat(cppFunctionModel.parameters).isEmpty()
    assertThat(cppFunctionModel.isUserCode).isTrue()
  }

  @Test
  fun `un-matching angle brackets shouldn't throw exceptions`() {
    val model = NodeNameParser.parseNodeName("malformed::method<(a, b)", false)
    assertThat(model).isInstanceOf(CppFunctionModel::class.java)
    // Instead of throwing an exception when finding matching parenthesis, we include all the parenthesis in the method name.
    assertThat(model.name).isEqualTo("method<")
    assertThat(model.fullName).isEqualTo("malformed::method<")
    assertThat(model.id).isEqualTo("malformed::method<[a, b]")
    val cppFunctionModel: CppFunctionModel = model as CppFunctionModel
    assertThat(cppFunctionModel.classOrNamespace).isEqualTo("malformed")
    assertThat(cppFunctionModel.parameters).hasSize(2)
    assertThat(cppFunctionModel.parameters[0]).isEqualTo("a")
    assertThat(cppFunctionModel.parameters[1]).isEqualTo("b")
    assertThat(cppFunctionModel.isUserCode).isFalse()
  }

  @Test
  fun `test cpp operator overloading method`() {
    (NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(int)",
                                  false) as CppFunctionModel).apply {
      expect(name = "operator<<",
             fullName = "std::__1::basic_ostream::operator<<",
             id = "std::__1::basic_ostream::operator<<[int]",
             classOrNamespace = "std::__1::basic_ostream",
             parameters = listOf("int"))
    }

    // Test a method with "operator" as a substring of its name
    (NodeNameParser.parseNodeName("void MyNameSpace::my_operator<int>::my_method(int)",
                                             false) as CppFunctionModel).apply {
      expect(name = "my_method",
             fullName = "MyNameSpace::my_operator::my_method",
             id = "MyNameSpace::my_operator::my_method[int]",
             classOrNamespace = "MyNameSpace::my_operator",
             parameters = listOf("int"))

    }
    (NodeNameParser.parseNodeName("void MyNameSpace::operator_my<int>::my_method(int)",
                                  false) as CppFunctionModel).apply {
      expect(name = "my_method",
             fullName = "MyNameSpace::operator_my::my_method",
             id = "MyNameSpace::operator_my::my_method[int]",
             classOrNamespace = "MyNameSpace::operator_my",
             parameters = listOf("int"))
    }

    // Test a method with "operator" as a substring of its parameter
    (NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(my_operator_type)",
                                  false) as CppFunctionModel).apply {
      expect(name = "operator<<",
             fullName = "std::__1::basic_ostream::operator<<",
             id = "std::__1::basic_ostream::operator<<[my_operator_type]",
             classOrNamespace = "std::__1::basic_ostream",
             parameters = listOf("my_operator_type"))
    }
  }

  @Test
  fun `test cpp operator overloading and templates in parameter and in namespace`() {
    (NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(MyTemplate<int>)",
                                  false) as CppFunctionModel).apply {
      expect(name = "operator<<",
             fullName = "std::__1::basic_ostream::operator<<",
             id = "std::__1::basic_ostream::operator<<[MyTemplate]",
             classOrNamespace = "std::__1::basic_ostream",
             parameters = listOf("MyTemplate"))
    }
  }

  @Test
  fun `abi arch filename and vaddress passed to cpp model`() {
    val fileName = "myfile.so"
    val vAddress = 0x013F01F0D4L
    val model = NodeNameParser.parseNodeName("void MyNameSpace::my_method(int)", false, fileName, vAddress) as CppFunctionModel
    assertThat(model.fileName).isEqualTo(fileName)
    assertThat(model.vAddress).isEqualTo(vAddress)
  }

  @Test
  fun `test cpp operator bool overloading`() {
    (NodeNameParser.parseNodeName("MyNamespace::operator bool()",
                                  false) as CppFunctionModel).apply {
      expect(name = "operator bool",
             fullName = "MyNamespace::operator bool",
             id = "MyNamespace::operator bool[]",
             classOrNamespace = "MyNamespace",
             parameters = emptyList())
    }

    (NodeNameParser.parseNodeName("operator bool()",
                                  false) as CppFunctionModel).apply {
      expect(name = "operator bool",
             fullName = "operator bool",
             id = "operator bool[]",
             classOrNamespace = "",
             parameters = emptyList())
    }
  }

  @Test
  fun `test starts with operator removes template info`() {
    (NodeNameParser.parseNodeName("MyNamespace::operatorManager<Type>()",
                                  false) as CppFunctionModel).apply {
      expect(name = "operatorManager",
             fullName = "MyNamespace::operatorManager",
             id = "MyNamespace::operatorManager[]",
             classOrNamespace = "MyNamespace",
             parameters = emptyList())
    }
  }

  @Test
  fun `test java methods parsing`() {
    var model = NodeNameParser.parseNodeName("java.util.String.toString", true)
    assertThat(model).isInstanceOf(JavaMethodModel::class.java)
    assertThat(model.name).isEqualTo("toString")
    assertThat(model.fullName).isEqualTo("java.util.String.toString")
    assertThat(model.id).isEqualTo("java.util.String.toString")
    var javaMethodModel: JavaMethodModel = model as JavaMethodModel
    assertThat(javaMethodModel.className).isEqualTo("java.util.String")
    assertThat(javaMethodModel.signature).isEmpty()

    model = NodeNameParser.parseNodeName("java.lang.Object.internalClone [DEDUPED]", true)
    assertThat(model).isInstanceOf(JavaMethodModel::class.java)
    assertThat(model.name).isEqualTo("internalClone [DEDUPED]")
    assertThat(model.fullName).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
    assertThat(model.id).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
    javaMethodModel = model as JavaMethodModel
    assertThat(javaMethodModel.className).isEqualTo("java.lang.Object")
    assertThat(javaMethodModel.signature).isEmpty()
  }

  @Test
  fun `test syscall parsing`() {
    val model = NodeNameParser.parseNodeName("write", true)
    assertThat(model).isInstanceOf(SyscallModel::class.java)
    assertThat(model.name).isEqualTo("write")
    assertThat(model.fullName).isEqualTo("write")
    assertThat(model.id).isEqualTo("write")
  }
}

private fun CppFunctionModel.expect(name: String, fullName: String, id: String, classOrNamespace: String, parameters: List<String>) {
  assertThat(this.name).isEqualTo(name)
  assertThat(this.fullName).isEqualTo(fullName)
  assertThat(this.id).isEqualTo(id)
  assertThat(this.classOrNamespace).isEqualTo(classOrNamespace)
  assertThat(this.parameters).isEqualTo(parameters)
}
