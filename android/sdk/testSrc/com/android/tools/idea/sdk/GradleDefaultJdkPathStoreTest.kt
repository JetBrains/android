/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.intellij.testFramework.LightPlatformTestCase

class GradleDefaultJdkPathStoreTest: LightPlatformTestCase() {

  override fun tearDown() {
    GradleDefaultJdkPathStore.jdkPath = null
    super.tearDown()
  }

  fun `test Given undefined default JDK When obtain path Then returns null`() {
    assertNull(GradleDefaultJdkPathStore.jdkPath)
  }

  fun `test Given stored default JDK When obtain path Then returns expected path`() {
    val storeJdkPath = "/jdk/path"
    GradleDefaultJdkPathStore.jdkPath = storeJdkPath
    assertEquals(storeJdkPath, GradleDefaultJdkPathStore.jdkPath)
  }
}