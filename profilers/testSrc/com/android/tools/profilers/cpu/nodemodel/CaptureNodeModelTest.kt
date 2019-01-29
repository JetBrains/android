// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.cpu.nodemodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureNodeModelTest {

  @Test
  fun testSingleNameModel() {
    val name = "Some weird name"
    val singleNameModel = SingleNameModel(name)
    assertThat(singleNameModel.name).isEqualTo(name)
    assertThat(singleNameModel.id).isEqualTo(name)
    assertThat(singleNameModel.fullName).isEqualTo(name)
  }

  @Test
  fun testJavaMethodModel() {
    val model = JavaMethodModel("someName", "MyClass", "signature")
    assertThat(model.name).isEqualTo("someName")
    assertThat(model.className).isEqualTo("MyClass")
    assertThat(model.signature).isEqualTo("signature")
    assertThat(model.fullName).isEqualTo("MyClass.someName")
    assertThat(model.id).isEqualTo("MyClass.someNamesignature")
  }

  @Test
  fun testNativeFunctionModel() {
    val functionModel = CppFunctionModel.Builder("someName")
      .setIsUserCode(true)
      .setParameters("int, float")
      .setClassOrNamespace("MyNativeClass")
      .build()
    assertThat(functionModel.name).isEqualTo("someName")
    assertThat(functionModel.isUserCode).isTrue()
    assertThat(functionModel.parameters).hasSize(2)
    assertThat(functionModel.parameters[0]).isEqualTo("int")
    assertThat(functionModel.parameters[1]).isEqualTo("float")
    assertThat(functionModel.fullName).isEqualTo("MyNativeClass::someName")
    assertThat(functionModel.classOrNamespace).isEqualTo("MyNativeClass")
    assertThat(functionModel.id).isEqualTo("MyNativeClass::someName[int, float]")
    assertThat(functionModel).isInstanceOf(NativeNodeModel::class.java)

    val syscallModel = SyscallModel("write")
    assertThat(syscallModel.name).isEqualTo("write")
    assertThat(syscallModel.fullName).isEqualTo("write")
    assertThat(syscallModel.id).isEqualTo("write")
    assertThat(syscallModel).isInstanceOf(NativeNodeModel::class.java)

    var noSymbolModel = NoSymbolModel("[kernel.kallsyms]+0xff00ff")
    assertThat(noSymbolModel.name).isEqualTo("[kernel.kallsyms]+0xff00ff")
    assertThat(noSymbolModel.fullName).isEqualTo("[kernel.kallsyms]+0xff00ff")
    assertThat(noSymbolModel.id).isEqualTo("[kernel.kallsyms]+0xff00ff")
    assertThat(noSymbolModel.isKernel).isTrue()
    assertThat(noSymbolModel).isInstanceOf(NativeNodeModel::class.java)

    noSymbolModel = NoSymbolModel("non-kernel.so+0xff00ff")
    assertThat(noSymbolModel.name).isEqualTo("non-kernel.so+0xff00ff")
    assertThat(noSymbolModel.fullName).isEqualTo("non-kernel.so+0xff00ff")
    assertThat(noSymbolModel.id).isEqualTo("non-kernel.so+0xff00ff")
    assertThat(noSymbolModel.isKernel).isFalse()
  }
}
