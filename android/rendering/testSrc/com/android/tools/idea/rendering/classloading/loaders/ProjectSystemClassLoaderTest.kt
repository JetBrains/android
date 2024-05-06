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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.util.io.write
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class ProjectSystemClassLoaderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @After
  fun tearDown() {
    StudioFlags.PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT.clearOverride()
  }

  @Test
  fun `check load from project`() {
    val outputDirectory = Files.createTempDirectory("out")
    val classFile1 = outputDirectory.resolve("file1").write("contents1").toFile()
    val classContent1 = ClassContent.loadFromFile(classFile1)
    val classFile2 = outputDirectory.resolve("file2").write("contents2").toFile()
    val classContent2 = ClassContent.loadFromFile(classFile2)
    val classes = mapOf(
      "a.class1" to classContent1,
      "a.class2" to classContent2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("not.found.class"))
    assertEquals(0, loader.getClassCache().count())
    assertArrayEquals(classFile1.readBytes(), loader.loadClass("a.class1"))
    assertArrayEquals(classFile2.readBytes(), loader.loadClass("a.class2"))
    assertEquals(2, loader.getClassCache().count())

    // Invalidate and reload one class
    loader.invalidateCaches()
    assertEquals(0, loader.getClassCache().count())
    assertArrayEquals(classFile1.readBytes(), loader.loadClass("a.class1"))
    assertEquals(1, loader.getClassCache().count())
  }

  @Test
  fun `test files removed`() {
    val outputDirectory = Files.createTempDirectory("out")
    val classFile1 = outputDirectory.resolve("file1").write("contents1").toFile()
    val loader = ProjectSystemClassLoader {
      if (it == "a.class1" && classFile1.isFile) ClassContent.loadFromFile(classFile1) else null
    }
    assertArrayEquals(classFile1.readBytes(), loader.loadClass("a.class1"))
    assertTrue(classFile1.delete())
    assertNull(loader.loadClass("a.class1"))
  }

  @Test
  fun `verify loaded classes`() {
    val outputDirectory = Files.createTempDirectory("out")
    val classFile1 = outputDirectory.resolve("file1").write("contents1").toFile()
    val classContent1 = ClassContent.loadFromFile(classFile1)
    val classFile2 = outputDirectory.resolve("file2").write("contents2").toFile()
    val classContent2 = ClassContent.loadFromFile(classFile2)
    val classes = mapOf(
      "a.class1" to classContent1,
      "a.class2" to classContent2
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertArrayEquals(classFile1.readBytes(), loader.loadClass("a.class1"))
    assertArrayEquals(classFile2.readBytes(), loader.loadClass("a.class2"))
    assertEquals(2, loader.getClassCache().count())
    assertTrue(classContent1.isUpToDate())
    assertTrue(classContent2.isUpToDate())

    // Simulate a file modification via timestamp update
    classFile1.setLastModified(111)
    assertFalse(loader.isUpToDate())

    // Now invalidate and reload class1
    loader.invalidateCaches()
    assertTrue(loader.isUpToDate())
    assertEquals(0, loader.getClassCache().count())
  }

  @Test
  fun `verify platform classes are not loaded`() {
    val outputDirectory = Files.createTempDirectory("out")
    val classFile1 = outputDirectory.resolve("file1").write("contents1").toFile()
    val classContent1 = ClassContent.loadFromFile(classFile1)
    val classFile2 = outputDirectory.resolve("file2").write("contents2").toFile()
    val classContent2 = ClassContent.loadFromFile(classFile2)
    val classFile3 = outputDirectory.resolve("file3").write("contents3").toFile()
    val classContent3 = ClassContent.loadFromFile(classFile3)
    val classes = mapOf(
      "_layoutlib_._internal_..class1" to classContent1,
      "java.lang.Test" to classContent2,
      "test.package.A" to classContent3
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("_layoutlib_._internal_..Class1"))
    assertNull(loader.loadClass("java.lang.Test"))
    assertArrayEquals(classFile3.readBytes(), loader.loadClass("test.package.A"))
    assertEquals(1, loader.getClassCache().count())
  }

  /**
   * Regression test for b/216309775. If a class is not found, but then added by a build, the project class loader should pick it up.
   */
  @Test
  fun `verify failed class loads are not cached`() {
    val outputDirectory = Files.createTempDirectory("out")
    val classFile1 = outputDirectory.resolve("file1").write("contents1").toFile()
    val classContent1 = ClassContent.loadFromFile(classFile1)
    val classFile2 = outputDirectory.resolve("file2").write("contents2").toFile()
    val classContent2 = ClassContent.loadFromFile(classFile2)
    val classes = mutableMapOf(
      "test.package.A" to classContent1
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }

    assertNull(loader.loadClass("test.package.B"))
    classes["test.package.B"] = classContent2
    assertNotNull(loader.loadClass("test.package.B"))
  }

  /**
   * Regression test for b/218453131 where we need to check that we get the latest version of the class file and not the
   * VFS cached version.
   */
  @Test
  fun `ensure latest version of the class file is accessed`() {
    val tempDirectory = Files.createTempDirectory("out").toFile()

    val classFile = tempDirectory.resolve("A.class").also {
      it.writeText("Initial content")
    }

    val loader = ProjectSystemClassLoader { fqcn ->
      when (fqcn) {
        "test.package.A" -> ClassContent.loadFromFile(classFile)
        else -> null
      }
    }

    assertEquals("Initial content", String(loader.loadClass("test.package.A")!!))

    // Write the file externally (not using VFS) and check the contents
    classFile.writeText("Updated content").also {
      classFile.setLastModified(111)
    }
    assertEquals("Updated content", String(loader.loadClass("test.package.A")!!))
  }

  @Test
  fun `loading classes from jar sources`() {
    val tempDirectory = Files.createTempDirectory("out").toFile()
    val outputJar = tempDirectory.resolve("classes.jar")

    createJarFile(outputJar.toPath(), mapOf(
      "ClassA.class" to "contents1".encodeToByteArray(),
      "ClassB.class" to "contents2".encodeToByteArray(),
      "test/package/ClassC.class" to "contents3".encodeToByteArray(),
    ))

    val classes = mutableMapOf(
      "A" to ClassContent.fromJarEntryContent(outputJar, "contents1".encodeToByteArray()),
      "B" to ClassContent.fromJarEntryContent(outputJar, "contents2".encodeToByteArray()),
      "test.package.C" to ClassContent.fromJarEntryContent(outputJar, "contents3".encodeToByteArray())
    )
    val loader = ProjectSystemClassLoader {
      classes[it]
    }
    assertEquals("contents1", String(loader.loadClass("A")!!))
    assertEquals("contents2", String(loader.loadClass("B")!!))
    assertEquals("contents3", String(loader.loadClass("test.package.C")!!))
  }
}