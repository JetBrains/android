/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.crashlytics

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class FirebaseCrashlyticsModelTest : GradleFileModelTestCase() {
  @Test
  fun testParseFirebaseCrashlytics() {
    writeToBuildFile(TestFile.PARSE_FIREBASE_CRASHLYTICS)
    val buildModel = gradleBuildModel
    val firebaseCrashlytics = buildModel.android().buildTypes().first { it.name() == "release" }.firebaseCrashlytics();
    assertEquals("nativeSymbolUploadEnabled", false, firebaseCrashlytics.nativeSymbolUploadEnabled())
  }

  @Test
  fun testSetNativeSymbolUploadEnabled() {
    writeToBuildFile("")
    val buildModel = gradleBuildModel
    buildModel.android().buildTypes().first { it.name() == "release" }.firebaseCrashlytics().nativeSymbolUploadEnabled().setValue(true)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_NATIVE_SYMBOL_UPLOAD_ENABLED_EXPECTED)

    buildModel.android().buildTypes().first { it.name() == "release" }.let {
      assertEquals("nativeSymbolUploadEnabled", true, it.firebaseCrashlytics().nativeSymbolUploadEnabled())
    }
  }

  @Test
  fun testRemoveAndApply() {
    writeToBuildFile(TestFile.PARSE_FIREBASE_CRASHLYTICS)
    val buildModel = gradleBuildModel
    val firebaseCrashlytics = buildModel.android().buildTypes().first { it.name() == "release" }.firebaseCrashlytics();
    assertEquals("nativeSymbolUploadEnabled", false, firebaseCrashlytics.nativeSymbolUploadEnabled())
    firebaseCrashlytics.nativeSymbolUploadEnabled().delete()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")
  }

  enum class TestFile(val path: @SystemDependent String): TestFileName {
    PARSE_FIREBASE_CRASHLYTICS("parseFirebaseCrashlytics"),
    SET_NATIVE_SYMBOL_UPLOAD_ENABLED_EXPECTED("setNativeSymbolUploadEnabledExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/firebaseCrashlyticsModel/$path", extension)
    }
  }
}