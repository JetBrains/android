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
package com.android.tools.profilers.cpu

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class MethodModelTest {

  @Test
  fun testCommonMethodsAndDefaultValues() {
    val model = MethodModel.Builder("someName").setSignature("signature").setParameters("int, float").build()
    // Parameters set
    assertThat(model.name).isEqualTo("someName")
    assertThat(model.signature).isEqualTo("signature")
    assertThat(model.parameters).isEqualTo("int, float")

    // Parameters that use default value
    assertThat(model.classOrNamespace).isEmpty()
    assertThat(model.separator).isEmpty()
    assertThat(model.isNative).isFalse()

    // Parameters derived from others
    assertThat(model.fullName).isEqualTo("someName")
    assertThat(model.id).isEqualTo("someNamesignature")
  }

  @Test
  fun settingJavaClassNameSetsClassNameAndSeparator() {
    val model = MethodModel.Builder("someName").setSignature("signature").setParameters("int, float").setJavaClassName("MyClass").build()
    assertThat(model.name).isEqualTo("someName")
    assertThat(model.signature).isEqualTo("signature")
    assertThat(model.parameters).isEqualTo("int, float")
    assertThat(model.isNative).isFalse()
    assertThat(model.fullName).isEqualTo("MyClass.someName")
    // These should be set by setJavaClassName
    assertThat(model.classOrNamespace).isEqualTo("MyClass")
    assertThat(model.separator).isEqualTo(".")
    // As this is a non-native method, ID uses signature (not parameters)
    assertThat(model.id).isEqualTo("MyClass.someNamesignature")
  }

  @Test
  fun settingNativeNamespaceSetsClassNameSeparatorAndIsNative() {
    val model = MethodModel.Builder("someName")
        .setSignature("signature")
        .setParameters("int, float")
        .setNativeNamespaceAndClass("MyNativeClass")
        .build()
    assertThat(model.name).isEqualTo("someName")
    assertThat(model.signature).isEqualTo("signature")
    assertThat(model.parameters).isEqualTo("int, float")
    assertThat(model.fullName).isEqualTo("MyNativeClass::someName")
    // These should be set by setNativeNamespaceAndClass
    assertThat(model.classOrNamespace).isEqualTo("MyNativeClass")
    assertThat(model.separator).isEqualTo("::")
    assertThat(model.isNative).isTrue()
    // As this is a non-native method, ID uses parameters (not parameters)
    assertThat(model.id).isEqualTo("MyNativeClass::someNameint, float")
  }

  @Test
  fun nativeNamespaceAndJavaClassNameAreMutuallyExclusive() {
    // Trying to call setNativeNamespaceAndClass after setJavaClassName should throw an Exception
    try {
      MethodModel.Builder("someName").setJavaClassName("MyClass").setNativeNamespaceAndClass("NativeClass").build()
      fail()
    }
    catch (expected: IllegalStateException) {
      assertThat(expected.message).contains("Native namespace/class can't be set")
    }

    // Trying to call setJavaClassName after setNativeNamespaceAndClass should throw an Exception
    try {
      MethodModel.Builder("someName").setNativeNamespaceAndClass("NativeClass").setJavaClassName("MyClass").build()
      fail()
    }
    catch (expected: IllegalStateException) {
      assertThat(expected.message).contains("Java class name can't be set")
    }
  }
}
