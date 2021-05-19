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
package com.android.tools.idea.lang.databinding

import com.android.testutils.TestUtils
import com.intellij.openapi.application.ex.PathManagerEx
import java.io.File

object LangDataBindingTestData {
  const val PROJECT_WITH_DATA_BINDING_SUPPORT = "projects/projectWithDataBindingSupport"
  const val PROJECT_WITH_DATA_BINDING_ANDROID_X = "projects/projectWithDataBindingAndroidX"
}

fun getTestDataPath(): String {
  val adtPath = TestUtils.getWorkspaceFile("tools/adt/idea/android-lang-databinding/testData").path
  return if (File(adtPath).exists()) adtPath else PathManagerEx.findFileUnderCommunityHome("android/android-lang-databinding/testData").path
}