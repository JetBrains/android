/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.lang.Boolean
import kotlin.io.path.div
import kotlin.io.path.exists

@RunWith(Parameterized::class)
class AndroidManifestUtilTest {

  @Parameter(0)
  lateinit var expectedHasAndroidManifest: Boolean

  @Parameter(1)
  lateinit var androidManifestXmlDir: String

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  companion object {
    @JvmStatic
    @Parameters(name = "{index}: expectedHasAndroidManifest:{0}, androidManifestXmlDir:{1}")
    fun produceParamters(): List<Array<Any>> = listOf(

      true to "app/src/main",
      true to "composeApp/src/main",
      true to "composeApp/src/androidMain",
      true to "some/deep/nested/gradle/project/src/main",
      true to "some/deep/nested/gradle/project/src/androidMain",

      false to "",
      false to "app",
      false to "app/build/main",
      false to "composeApp/build/main",
      false to "composeApp/src/jvmMain",
      false to "composeApp/src/wasmJsMain",
    ).map { arrayOf(it.first, it.second) }
  }

  @Test
  fun `finds Android Manifest XML in expected locations`() {
    val root = temporaryFolder.root.toPath()

    assertFalse(hasAndroidManifest(root))

    if (!(root / androidManifestXmlDir).exists()) {
      temporaryFolder.newFolder(androidManifestXmlDir)
    }
    temporaryFolder.newFile("$androidManifestXmlDir/AndroidManifest.xml")

    assertEquals(expectedHasAndroidManifest, hasAndroidManifest(root))
  }
}