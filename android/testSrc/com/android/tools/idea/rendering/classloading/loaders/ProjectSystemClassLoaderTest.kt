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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException

/**
 * [FakeVirtualFile] that supports [VirtualFile.contentsToByteArray].
 *
 * The constructor requires a parent [VirtualFile] and a file name. Optionally a last modified timestamp can be passed as
 * [modificationTimeStamp].
 */
private open class TestVirtualFile(parent: VirtualFile,
                                   name: String,
                                   var modificationTimeStamp: Long = System.currentTimeMillis()) : FakeVirtualFile(parent, name) {
  var contents = ByteArray(0)
  override fun contentsToByteArray(): ByteArray = contents

  override fun isValid(): Boolean = true
  override fun getLength(): Long = contents.size.toLong()
  override fun getTimeStamp(): Long = modificationTimeStamp
}

class ProjectSystemClassLoaderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `check load from project`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    val virtualFile1 = TestVirtualFile(rootDir, "file1")
    val virtualFile2 = TestVirtualFile(rootDir, "file2")
    val classes = mapOf(
      "a.class1" to virtualFile1,
      "a.class2" to virtualFile2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("not.found.class"))
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertEquals(virtualFile2.contentsToByteArray(), loader.loadClass("a.class2"))
    assertEquals(2, loader.loadedVirtualFiles.count())

    // Invalidate and reload one class
    loader.invalidateCaches()
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertEquals(1, loader.loadedVirtualFiles.count())
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

  @Test
  fun `verify loaded classes`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    val virtualFile1 = TestVirtualFile(rootDir, "file1", 111)
    val virtualFile2 = TestVirtualFile(rootDir, "file2", 111)
    val classes = mapOf(
      "a.class1" to virtualFile1,
      "a.class2" to virtualFile2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertEquals(virtualFile2.contentsToByteArray(), loader.loadClass("a.class2"))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class2" }.third.isUpToDate(virtualFile2))

    // Simulate a file modification via timestamp update
    virtualFile1.modificationTimeStamp = 112
    assertFalse(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class2" }.third.isUpToDate(virtualFile2))

    // Now invalidate and reload class1
    loader.invalidateCaches()
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
  }

  @Test
  fun `verify platform classes are not loaded`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    val virtualFile1 = TestVirtualFile(rootDir, "file1")
    val virtualFile2 = TestVirtualFile(rootDir, "file2")
    val virtualFile3 = TestVirtualFile(rootDir, "file3")
    val classes = mapOf(
      "_layoutlib_._internal_..class1" to virtualFile1,
      "java.lang.Test" to virtualFile2,
      "test.package.A" to virtualFile3
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("_layoutlib_._internal_..Class1"))
    assertNull(loader.loadClass("java.lang.Test"))
    assertEquals(virtualFile3.contentsToByteArray(), loader.loadClass("test.package.A"))
    assertEquals(1, loader.loadedVirtualFiles.count())
  }

  /**
   * Regression test for b/216309775. If a class is not found, but then added by a build, the project class loader should pick it up.
   */
  @Test
  fun `verify failed class loads are not cached`() {
    val rootDir = projectRule.fixture.tempDirFixture.findOrCreateDir("test")
    val virtualFile1 = TestVirtualFile(rootDir, "file1")
    val virtualFile2 = TestVirtualFile(rootDir, "file2")
    val classes = mutableMapOf(
      "test.package.A" to virtualFile1
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("test.package.B"))
    classes["test.package.B"] = virtualFile2
    assertNotNull(loader.loadClass("test.package.B"))
  }
}