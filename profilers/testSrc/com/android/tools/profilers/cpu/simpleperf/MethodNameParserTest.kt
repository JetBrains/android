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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class MethodNameParserTest {

  @Test
  fun testCppMethodsParsing() {
    var model = MethodNameParser.parseMethodName("art::ArtMethod::Invoke()")
    assertThat(model.name).isEqualTo("Invoke")
    assertThat(model.className).isEqualTo("art::ArtMethod")
    assertThat(model.signature).isEmpty()
    assertThat(model.separator).isEqualTo("::")

    model = MethodNameParser.parseMethodName("art::interpreter::DoCall(bool, art::Thread*)")
    assertThat(model.name).isEqualTo("DoCall")
    assertThat(model.className).isEqualTo("art::interpreter")
    assertThat(model.signature).isEqualTo("bool, art::Thread*")
    assertThat(model.separator).isEqualTo("::")

    model = MethodNameParser.parseMethodName("art::SomeClass::add<int>()")
    assertThat(model.name).isEqualTo("add")
    assertThat(model.className).isEqualTo("art::SomeClass")
    assertThat(model.signature).isEmpty()
    assertThat(model.separator).isEqualTo("::")

    model = MethodNameParser.parseMethodName("Shader::Render(glm::detail::tmat4x4<float, (glm::precision)0>*)")
    assertThat(model.name).isEqualTo("Render")
    assertThat(model.className).isEqualTo("Shader")
    assertThat(model.signature).isEqualTo("glm::detail::tmat4x4*")
    assertThat(model.separator).isEqualTo("::")

    model = MethodNameParser.parseMethodName("art::StackVisitor::GetDexPc(bool) const")
    assertThat(model.name).isEqualTo("GetDexPc")
    assertThat(model.className).isEqualTo("art::StackVisitor")
    assertThat(model.signature).isEqualTo("bool")
    assertThat(model.separator).isEqualTo("::")

    model = MethodNameParser.parseMethodName("Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)")
    assertThat(model.name).isEqualTo("FuncTemplate")
    assertThat(model.className).isEqualTo("Type2")
    assertThat(model.signature).isEqualTo("Type4")
    assertThat(model.separator).isEqualTo("::")

    try {
      MethodNameParser.parseMethodName("malformed::method<)")
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Native method signature must have matching parentheses and brackets.")
    }
  }

  @Test
  fun testJavaMethodsParsing() {
    var model = MethodNameParser.parseMethodName("java.util.String.toString")
    assertThat(model.name).isEqualTo("toString")
    assertThat(model.className).isEqualTo("java.util.String")
    assertThat(model.signature).isEmpty()
    assertThat(model.separator).isEqualTo(".")

    model = MethodNameParser.parseMethodName("java.lang.Object.internalClone [DEDUPED]")
    assertThat(model.name).isEqualTo("internalClone [DEDUPED]")
    assertThat(model.className).isEqualTo("java.lang.Object")
    assertThat(model.signature).isEmpty()
    assertThat(model.separator).isEqualTo(".")
  }

  @Test
  fun testSyscallParsing() {
    val model = MethodNameParser.parseMethodName("write")
    assertThat(model.name).isEqualTo("write")
    assertThat(model.className).isEmpty()
    assertThat(model.signature).isEmpty()
    assertThat(model.separator).isEmpty()
  }
}
