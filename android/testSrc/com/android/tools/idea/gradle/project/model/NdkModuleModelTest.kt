/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.ide.common.gradle.model.IdeNativeAndroidProjectImpl
import com.android.ide.common.gradle.model.stubs.NativeAndroidProjectStub
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.SkipNullAndEmptySerializationFilter
import com.intellij.serialization.WriteConfiguration
import junit.framework.TestCase
import nl.jqno.equalsverifier.EqualsVerifier
import java.io.File

class NdkModuleModelTest : AndroidGradleTestCase() {

  fun testVariantAbiNames() {
    loadProject(TestProjectPaths.HELLO_JNI)
    val appModule = project.findAppModule()
    val ndkModuleModel = get(appModule)
    TestCase.assertNotNull(ndkModuleModel)
    // Verify that the name contains both of variant and abi.
    assertThat(ndkModuleModel!!.ndkVariantNames).contains("arm8Release-x86")
  }

  fun testEqualsHash() {
    val equalsVerifier = EqualsVerifier.forClass(NdkModuleModel::class.java)
    equalsVerifier.verify()
  }

  fun testSerialization() {
    val original = NdkModuleModel("moduleName", File("some/path"), IdeNativeAndroidProjectImpl(NativeAndroidProjectStub()), listOf())
    val deserialized = assertSerializable(original)
    assertThat(original.ndkVariantNames).isEqualTo(deserialized.ndkVariantNames)
  }

  private inline fun <reified T : Any> assertSerializable(value : T) : T {
    val configuration = WriteConfiguration(
      allowAnySubTypes = true,
      binary = false,
      filter = SkipNullAndEmptySerializationFilter
    )
    val bytes = ObjectSerializer.instance.writeAsBytes(value, configuration)
    val deserialized = ObjectSerializer.instance.read(T::class.java, bytes, ReadConfiguration(allowAnySubTypes = true))
    val bytes2 = ObjectSerializer.instance.writeAsBytes(deserialized, configuration)
    TestCase.assertEquals(String(bytes), String(bytes2))
    TestCase.assertEquals(value, deserialized)
    return deserialized
  }
}