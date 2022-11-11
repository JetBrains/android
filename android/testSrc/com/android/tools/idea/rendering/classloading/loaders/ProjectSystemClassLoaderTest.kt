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
import com.android.tools.idea.testing.writeChild
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

private class TestVirtualFile(delegate: VirtualFile) : DelegateVirtualFile(delegate) {
  var _modificationStamp: Long? = null

  override fun getModificationStamp(): Long = _modificationStamp ?: super.getModificationStamp()
  override fun getTimeStamp(): Long = _modificationStamp ?: super.getTimeStamp()
}

class ProjectSystemClassLoaderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `check load from project`() {
    val outputDirectory = Files.createTempDirectory("out")
    val virtualFile1 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file1", "contents1")
    val virtualFile2 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file2", "contents2")
    val classes = mapOf(
      "a.class1" to virtualFile1,
      "a.class2" to virtualFile2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("not.found.class"))
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertArrayEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertArrayEquals(virtualFile2.contentsToByteArray(), loader.loadClass("a.class2"))
    assertEquals(2, loader.loadedVirtualFiles.count())

    // Invalidate and reload one class
    loader.invalidateCaches()
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertArrayEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertEquals(1, loader.loadedVirtualFiles.count())
  }

  @Test
  fun `test files removed`() {
    val outputDirectory = Files.createTempDirectory("out")
    val virtualFile = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file1", "contents1")
    val loader = ProjectSystemClassLoader {
      if (it == "a.class1") virtualFile else null
    }
    assertArrayEquals(virtualFile.contentsToByteArray(), loader.loadClass("a.class1"))
    runWriteActionAndWait { virtualFile.delete(this) }
    assertNull(loader.loadClass("a.class1"))
  }

  @Test
  fun `verify loaded classes`() {
    val outputDirectory = Files.createTempDirectory("out")
    val virtualFile1 = TestVirtualFile(VfsUtil.findFile(outputDirectory, true)!!.writeChild("file1", "contents1"))
    val virtualFile2 = TestVirtualFile(VfsUtil.findFile(outputDirectory, true)!!.writeChild("file2", "contents2"))
    val classes = mapOf(
      "a.class1" to virtualFile1,
      "a.class2" to virtualFile2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertArrayEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertArrayEquals(virtualFile2.contentsToByteArray(), loader.loadClass("a.class2"))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class2" }.third.isUpToDate(virtualFile2))

    // Simulate a file modification via timestamp update
    virtualFile1._modificationStamp = 111
    assertFalse(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class2" }.third.isUpToDate(virtualFile2))

    // Now invalidate and reload class1
    loader.invalidateCaches()
    assertEquals(0, loader.loadedVirtualFiles.count())
    assertArrayEquals(virtualFile1.contentsToByteArray(), loader.loadClass("a.class1"))
    assertTrue(loader.loadedVirtualFiles.single { it.first == "a.class1" }.third.isUpToDate(virtualFile1))
  }

  @Test
  fun `verify platform classes are not loaded`() {
    val outputDirectory = Files.createTempDirectory("out")
    val virtualFile1 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file1", "contents1")
    val virtualFile2 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file2", "contents2")
    val virtualFile3 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file3", "contents3")
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
    assertArrayEquals(virtualFile3.contentsToByteArray(), loader.loadClass("test.package.A"))
    assertEquals(1, loader.loadedVirtualFiles.count())
  }

  /**
   * Regression test for b/216309775. If a class is not found, but then added by a build, the project class loader should pick it up.
   */
  @Test
  fun `verify failed class loads are not cached`() {
    val outputDirectory = Files.createTempDirectory("out")
    val virtualFile1 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file1", "contents1")
    val virtualFile2 = VfsUtil.findFile(outputDirectory, true)!!.writeChild("file2", "contents2")
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

  /**
   * Regression test for b/218453131 where we need to check that we get the latest version of the class file and not the
   * VFS cached version.
   */
  @Test
  fun `ensure latest version of the class file is accessed`() {
    val tempDirectory = VfsUtil.findFileByIoFile(Files.createTempDirectory("out").toFile(), true)!!

    val classFile = runWriteActionAndWait {
      val classFile = tempDirectory.createChildData(this, "A.class")
      val classFileContents = "Initial content"
      classFile.setBinaryContent(classFileContents.toByteArray())
      classFile
    }

    val classes = mutableMapOf(
      "test.package.A" to classFile
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertEquals("Initial content", String(loader.loadClass("test.package.A")!!))

    // Write the file externally (not using VFS) and check the contents
    Files.writeString(classFile.toNioPath(), "Updated content")
    assertEquals("Updated content", String(loader.loadClass("test.package.A")!!))
  }

  private fun createJarFile(outputJar: Path, contents: Map<String, ByteArray>) {
    assertTrue(FileSystemProvider.installedProviders().any { it.scheme == "jar" })

    val outputJarUri = URI.create("jar:file:$outputJar")
    FileSystems.newFileSystem(outputJarUri, mapOf("create" to "true")).use {
      contents.forEach { pathString, contents ->
        val path = it.getPath(pathString)
        // Create parent directories if any
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, contents)
      }
    }
  }
}