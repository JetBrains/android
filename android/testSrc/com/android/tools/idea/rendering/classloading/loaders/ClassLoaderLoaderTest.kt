/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading.loaders

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClassLoaderLoaderTest {
  @Test
  fun `check remove jar in UrlClassLoader`() {
    val jarSource = File(AndroidTestBase.getTestDataPath(), "rendering/renderClassLoader/lib.jar")
    val testJarFile = File.createTempFile("RenderClassLoader", ".jar")
    FileUtil.copy(jarSource, testJarFile)

    val loader = ClassLoaderLoader(
      createUrlClassLoader(listOf(testJarFile.toPath()), false)
    )
    assertNotNull(loader.loadClass("com.myjar.MyJarClass"))
    assertTrue(testJarFile.delete())
    assertNull("Class should not be available anymore", loader.loadClass("com.myjar.MyJarClass"))
  }
}