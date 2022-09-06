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
    NodeNameParser.parseNodeName("art::ArtMethod::Invoke()", true).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("Invoke")
      assertThat(fullName).isEqualTo("art::ArtMethod::Invoke")
      assertThat(id).isEqualTo("art::ArtMethod::Invoke[]")
      assertThat(classOrNamespace).isEqualTo("art::ArtMethod")
      assertThat(parameters).isEmpty()
      assertThat(isUserCode).isTrue()
    }

    NodeNameParser.parseNodeName("art::interpreter::DoCall(bool, art::Thread*)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("DoCall")
      assertThat(fullName).isEqualTo("art::interpreter::DoCall")
      assertThat(id).isEqualTo("art::interpreter::DoCall[bool, art::Thread*]")
      assertThat(classOrNamespace).isEqualTo("art::interpreter")
      assertThat(parameters).containsExactly("bool", "art::Thread*")
      assertThat(isUserCode).isFalse()
    }

    NodeNameParser.parseNodeName("art::SomeClass::add<int>()", true).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("add")
      assertThat(fullName).isEqualTo("art::SomeClass::add")
      assertThat(id).isEqualTo("art::SomeClass::add[]")
      assertThat(classOrNamespace).isEqualTo("art::SomeClass")
      assertThat(parameters).isEmpty()
      assertThat(isUserCode).isTrue()
    }

    NodeNameParser.parseNodeName("Shader::Render(glm::detail::tmat4x4<float, (glm::precision)0>*)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("Render")
      assertThat(fullName).isEqualTo("Shader::Render")
      assertThat(id).isEqualTo("Shader::Render[glm::detail::tmat4x4*]")
      assertThat(classOrNamespace).isEqualTo("Shader")
      assertThat(parameters).containsExactly("glm::detail::tmat4x4*")
      assertThat(isUserCode).isFalse()
    }

    NodeNameParser.parseNodeName("art::StackVisitor::GetDexPc(bool) const", true).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("GetDexPc")
      assertThat(fullName).isEqualTo("art::StackVisitor::GetDexPc")
      assertThat(id).isEqualTo("art::StackVisitor::GetDexPc[bool]")
      assertThat(classOrNamespace).isEqualTo("art::StackVisitor")
      assertThat(parameters).containsExactly("bool")
      assertThat(isUserCode).isTrue()
    }

    NodeNameParser.parseNodeName("Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("FuncTemplate")
      assertThat(fullName).isEqualTo("Type2::FuncTemplate")
      assertThat(id).isEqualTo("Type2::FuncTemplate[Type4]")
      assertThat(classOrNamespace).isEqualTo("Type2")
      assertThat(parameters).containsExactly("Type4")
      assertThat(isUserCode).isFalse()
    }

    NodeNameParser.parseNodeName("int Abc123(bool)", true).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("Abc123")
      assertThat(fullName).isEqualTo("Abc123")
      assertThat(id).isEqualTo("Abc123[bool]")
      assertThat(classOrNamespace).isEmpty()
      assertThat(parameters).containsExactly("bool")
    }
  }

  @Test
  fun `un-matching parenthesis shouldn't throw exceptions`() {
    NodeNameParser.parseNodeName("malformed::method(a))", true).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      // Instead of throwing an exception when finding matching parenthesis, we include all the parenthesis in the method name.
      assertThat(name).isEqualTo("method(a))")
      assertThat(fullName).isEqualTo("malformed::method(a))")
      assertThat(id).isEqualTo("malformed::method(a))[]")
      assertThat(classOrNamespace).isEqualTo("malformed")
      assertThat(parameters).isEmpty()
      assertThat(isUserCode).isTrue()
    }
  }

  @Test
  fun `un-matching angle brackets shouldn't throw exceptions`() {
    NodeNameParser.parseNodeName("malformed::method<(a, b)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      // Instead of throwing an exception when finding matching parenthesis, we include all the parenthesis in the method name.
      assertThat(name).isEqualTo("method<")
      assertThat(fullName).isEqualTo("malformed::method<")
      assertThat(id).isEqualTo("malformed::method<[a, b]")
      assertThat(classOrNamespace).isEqualTo("malformed")
      assertThat(parameters).containsExactly("a", "b")
      assertThat(isUserCode).isFalse()
    }
  }

  @Test
  fun `test cpp operator overloading method`() {
    NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(int)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operator<<")
      assertThat(fullName).isEqualTo("std::__1::basic_ostream::operator<<")
      assertThat(id).isEqualTo("std::__1::basic_ostream::operator<<[int]")
      assertThat(classOrNamespace).isEqualTo("std::__1::basic_ostream")
      assertThat(parameters).containsExactly("int")
    }

    // Test a method with "operator" as a substring of its name
    NodeNameParser.parseNodeName("void MyNameSpace::my_operator<int>::my_method(int)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("my_method")
      assertThat(fullName).isEqualTo("MyNameSpace::my_operator::my_method")
      assertThat(id).isEqualTo("MyNameSpace::my_operator::my_method[int]")
      assertThat(classOrNamespace).isEqualTo("MyNameSpace::my_operator")
      assertThat(parameters).containsExactly("int")
    }

    NodeNameParser.parseNodeName("void MyNameSpace::operator_my<int>::my_method(int)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("my_method")
      assertThat(fullName).isEqualTo("MyNameSpace::operator_my::my_method")
      assertThat(id).isEqualTo("MyNameSpace::operator_my::my_method[int]")
      assertThat(classOrNamespace).isEqualTo("MyNameSpace::operator_my")
      assertThat(parameters).containsExactly("int")
    }

    // Test a method with "operator" as a substring of its parameter
    NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(my_operator_type)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operator<<")
      assertThat(fullName).isEqualTo("std::__1::basic_ostream::operator<<")
      assertThat(id).isEqualTo("std::__1::basic_ostream::operator<<[my_operator_type]")
      assertThat(classOrNamespace).isEqualTo("std::__1::basic_ostream")
      assertThat(parameters).containsExactly("my_operator_type")
    }
  }

  @Test
  fun `test cpp operator overloading and templates in parameter and in namespace`() {
    NodeNameParser.parseNodeName("std::__1::basic_ostream<char, std::__1::char_traits<char> >::operator<<(MyTemplate<int>)", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operator<<")
      assertThat(fullName).isEqualTo("std::__1::basic_ostream::operator<<")
      assertThat(id).isEqualTo("std::__1::basic_ostream::operator<<[MyTemplate]")
      assertThat(classOrNamespace).isEqualTo("std::__1::basic_ostream")
      assertThat(parameters).containsExactly("MyTemplate")
    }
  }

  @Test
  fun `abi arch filename and vaddress passed to cpp model`() {
    val expectedFileName = "myfile.so"
    val expectedVAddress = 0x013F01F0D4L

    NodeNameParser.parseNodeName("void MyNameSpace::my_method(int)", false, expectedFileName, expectedVAddress).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(fileName).isEqualTo(expectedFileName)
      assertThat(vAddress).isEqualTo(expectedVAddress)
    }
  }

  @Test
  fun `test cpp operator bool overloading`() {
    NodeNameParser.parseNodeName("MyNamespace::operator bool()", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operator bool")
      assertThat(fullName).isEqualTo("MyNamespace::operator bool")
      assertThat(id).isEqualTo("MyNamespace::operator bool[]")
      assertThat(classOrNamespace).isEqualTo("MyNamespace")
      assertThat(parameters).isEmpty()
    }

    NodeNameParser.parseNodeName("operator bool()", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operator bool")
      assertThat(fullName).isEqualTo("operator bool")
      assertThat(id).isEqualTo("operator bool[]")
      assertThat(classOrNamespace).isEmpty()
      assertThat(parameters).isEmpty()
    }
  }

  @Test
  fun `test starts with operator removes template info`() {
    NodeNameParser.parseNodeName("MyNamespace::operatorManager<Type>()", false).apply {
      assertThat(this).isInstanceOf(CppFunctionModel::class.java)
      this as CppFunctionModel
      assertThat(name).isEqualTo("operatorManager")
      assertThat(fullName).isEqualTo("MyNamespace::operatorManager")
      assertThat(id).isEqualTo("MyNamespace::operatorManager[]")
      assertThat(classOrNamespace).isEqualTo("MyNamespace")
      assertThat(parameters).isEmpty()
    }
  }

  @Test
  fun `test java methods parsing`() {
    NodeNameParser.parseNodeName("java.util.String.toString", true).apply {
      assertThat(this).isInstanceOf(JavaMethodModel::class.java)
      this as JavaMethodModel
      assertThat(name).isEqualTo("toString")
      assertThat(fullName).isEqualTo("java.util.String.toString")
      assertThat(id).isEqualTo("java.util.String.toString")
      assertThat(className).isEqualTo("java.util.String")
      assertThat(signature).isEmpty()
    }

    NodeNameParser.parseNodeName("java.lang.Object.internalClone [DEDUPED]", true).apply {
      assertThat(this).isInstanceOf(JavaMethodModel::class.java)
      this as JavaMethodModel
      assertThat(name).isEqualTo("internalClone [DEDUPED]")
      assertThat(fullName).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
      assertThat(id).isEqualTo("java.lang.Object.internalClone [DEDUPED]")
      assertThat(className).isEqualTo("java.lang.Object")
      assertThat(signature).isEmpty()
    }
  }

  @Test
  fun `test syscall parsing`() {
    NodeNameParser.parseNodeName("write", true).apply {
      assertThat(this).isInstanceOf(SyscallModel::class.java)
      assertThat(name).isEqualTo("write")
      assertThat(fullName).isEqualTo("write")
      assertThat(id).isEqualTo("write")
    }
  }
}
