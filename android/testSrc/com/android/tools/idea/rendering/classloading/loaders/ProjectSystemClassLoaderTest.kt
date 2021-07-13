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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException

/**
 * [FakeVirtualFile] that supports [VirtualFile.contentsToByteArray].
 */
private open class TestVirtualFile(parent: VirtualFile, name: String): FakeVirtualFile(parent, name) {
  private val contents = ByteArray(0)
  override fun contentsToByteArray(): ByteArray = contents
}

class ProjectSystemClassLoaderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `check load from project`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    val virtualFile1 = TestVirtualFile(rootDir, "file1")
    val virtualFile2 = TestVirtualFile(rootDir, "fil2")
    val classes = mapOf(
      "a.class1" to virtualFile1,
      "a.class2" to virtualFile2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("not.found.class"))
    assertEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertEquals(virtualFile2.contentsToByteArray(), loader.loadClass("a.class2"))
  }

  @Test
  fun `test files removed`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    var removed = false
    val virtualFile = object: TestVirtualFile(rootDir, "file1") {
      override fun isValid(): Boolean = !removed

      override fun contentsToByteArray(): ByteArray =
        if (removed) throw FileNotFoundException("") else super.contentsToByteArray()
    }
    val loader = ProjectSystemClassLoader {
      if (it == "a.class1") virtualFile else null
    }

    assertEquals(virtualFile.contentsToByteArray(), loader.loadClass("a.class1"))
    // Simulate file removal
    removed = true
    assertNull(loader.loadClass("a.class1"))
  }
}