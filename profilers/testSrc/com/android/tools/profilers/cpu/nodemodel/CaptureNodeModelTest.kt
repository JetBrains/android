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
  fun testDummyAndSingleNameModels() {
    val dummyModel = DummyModel()
    assertThat(dummyModel.name).isEqualTo(DummyModel.DUMMY_NODE)
    assertThat(dummyModel.id).isEqualTo(DummyModel.DUMMY_NODE)
    assertThat(dummyModel.fullName).isEqualTo(DummyModel.DUMMY_NODE)

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
    val model = NativeFunctionModel.Builder("someName").setParameters("int, float").setClassOrNamespace("MyNativeClass").build()
    assertThat(model.name).isEqualTo("someName")
    assertThat(model.parameters).hasSize(2)
    assertThat(model.parameters[0]).isEqualTo("int")
    assertThat(model.parameters[1]).isEqualTo("float")
    assertThat(model.fullName).isEqualTo("MyNativeClass::someName")
    assertThat(model.classOrNamespace).isEqualTo("MyNativeClass")
    assertThat(model.id).isEqualTo("MyNativeClass::someName[int, float]")
  }
}
