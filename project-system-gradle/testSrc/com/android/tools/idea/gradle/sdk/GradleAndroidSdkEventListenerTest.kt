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
package com.android.tools.idea.gradle.sdk

import com.android.test.testutils.TestUtils.getSdk
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.sdk.AndroidSdkPathStore
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runWriteActionAndWait
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GradleAndroidSdkEventListenerTest  {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withAndroidModel()

  @After
  fun tearDown() {
    IdeSdks.removeJdksOn(androidProjectRule.testRootDisposable)
    runWriteActionAndWait {
      AndroidSdkPathStore.getInstance().androidSdkPath = null
    }
  }

  @Test
  fun testSetAndroidSdkPathUpdatingLocalPropertiesFile() {
    val androidSdkPath = getSdk().toFile()
    runWriteActionAndWait {
      IdeSdks.getInstance().setAndroidSdkPath(androidSdkPath)
    }

    val localProperties = LocalProperties(getBaseDirPath(androidProjectRule.project))
    assertEquals(androidSdkPath, localProperties.androidSdkPath)
  }
}