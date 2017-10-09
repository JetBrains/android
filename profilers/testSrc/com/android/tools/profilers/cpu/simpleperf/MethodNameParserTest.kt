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
