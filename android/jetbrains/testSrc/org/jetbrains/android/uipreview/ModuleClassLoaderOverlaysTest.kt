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
package org.jetbrains.android.uipreview

import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.utils.FileUtils.toSystemIndependentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Paths

fun loadClassBytes(c: Class<*>): ByteArray {
  val className = "${Type.getInternalName(c)}.class"
  c.classLoader.getResourceAsStream(className)!!.use { return it.readBytes() }
}

class TestClass
val testClassName: String = TestClass::class.java.canonicalName

internal class ModuleClassLoaderOverlaysTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val buildTargetReference: BuildTargetReference
    get() = BuildTargetReference.gradleOnly(projectRule.module)

  private val projectOverlayModificationCount: Long
    get() = ModuleClassLoaderOverlays.NotificationManager.getInstance(projectRule.project).modificationFlow.value

  private val moduleOverlayModificationCount: Long
    get() = ModuleClassLoaderOverlays.getInstance(buildTargetReference).modificationTracker.modificationCount

  @Test
  fun `empty overlay does not return classes`() {
    assertNull(ModuleClassLoaderOverlays.getInstance(buildTargetReference).classLoaderLoader.loadClass(testClassName))
  }

  @Test
  fun `invalidation updates the modification count`() {
    // Copy the classes into a temp directory to use as overlay
    val tempOverlayPath = Files.createTempDirectory("overlayTest")
    val packageDirPath = Files.createDirectories(tempOverlayPath.resolve(TestClass::class.java.packageName.replace(".", "/")))

    val moduleClassLoaderOverlay =
      ModuleClassLoaderOverlays.getInstance(buildTargetReference)
    moduleClassLoaderOverlay.pushOverlayPath(tempOverlayPath)
    val classFilePath = packageDirPath.resolve(TestClass::class.java.simpleName + ".class")
    Files.write(classFilePath, loadClassBytes(TestClass::class.java))
    assertNotNull(moduleClassLoaderOverlay.classLoaderLoader.loadClass(testClassName))

    assertEquals(1, projectOverlayModificationCount)
    assertEquals(1, moduleOverlayModificationCount)
    moduleClassLoaderOverlay.invalidateOverlayPaths()
    assertEquals(2, projectOverlayModificationCount)
    assertEquals(2, moduleOverlayModificationCount)
  }

  @Test
  fun `overlay finds classes`() {
    // Copy the classes into a temp directory to use as overlay
    val tempOverlayPath = Files.createTempDirectory("overlayTest")
    val packageDirPath = Files.createDirectories(tempOverlayPath.resolve(TestClass::class.java.packageName.replace(".", "/")))

    val classFilePath = packageDirPath.resolve(TestClass::class.java.simpleName + ".class")
    Files.write(classFilePath, loadClassBytes(TestClass::class.java))
    val moduleClassLoaderOverlay = ModuleClassLoaderOverlays.getInstance(
      buildTargetReference)
    moduleClassLoaderOverlay.pushOverlayPath(tempOverlayPath)
    assertTrue(moduleClassLoaderOverlay.containsClass(testClassName))
    assertNotNull(moduleClassLoaderOverlay.classLoaderLoader.loadClass(testClassName))

    // If deleted, the class should disappear
    Files.delete(classFilePath)
    assertFalse(moduleClassLoaderOverlay.containsClass(testClassName))
    assertNull(moduleClassLoaderOverlay.classLoaderLoader.loadClass(testClassName))
  }

  @Test
  fun `state loading`() {
    fun List<String>.asPlatformIndependent(): List<String> =
      map { toSystemIndependentPath(it) }

    val moduleClassLoaderOverlays = ModuleClassLoaderOverlays.getInstance(
      buildTargetReference)

    moduleClassLoaderOverlays.pushOverlayPath(Paths.get("/tmp/overlay2"))
    moduleClassLoaderOverlays.pushOverlayPath(Paths.get("/tmp/overlay1"))
    val state = moduleClassLoaderOverlays.state
    assertEquals(
      """
        /tmp/overlay1
        /tmp/overlay2
      """.trimIndent(),
      state.paths.asPlatformIndependent().joinToString("\n")
    )
    moduleClassLoaderOverlays.loadState(state)

    val state2 = moduleClassLoaderOverlays.state
    assertEquals(
      """
        /tmp/overlay1
        /tmp/overlay2
      """.trimIndent(),
      state2.paths.asPlatformIndependent().joinToString("\n")
    )
  }

  @Test
  fun `find classes in multiple overlays`() {
    val tempOverlayPath1 = Files.createTempDirectory("overlayTest1")
    val tempOverlayPath2 = Files.createTempDirectory("overlayTest2")

    // Create a few package directories in the overlays
    Files.createDirectories(tempOverlayPath1.resolve("a/b/c"))
    Files.createDirectories(tempOverlayPath2.resolve("d/e/f"))

    // Create a few fake classes in the overlays
    Files.write(tempOverlayPath1.resolve("a/b/c/TestClass.class"), ByteArray(0))
    Files.write(tempOverlayPath1.resolve("a/TestClass.class"), ByteArray(0))
    Files.write(tempOverlayPath2.resolve("d/e/OtherTestClass.class"), ByteArray(0))
    Files.write(tempOverlayPath2.resolve("d/e/f/OtherTestClass.class"), ByteArray(0))
    val moduleClassLoaderOverlays = ModuleClassLoaderOverlays.getInstance(buildTargetReference)
    moduleClassLoaderOverlays.pushOverlayPath(tempOverlayPath1)
    assertTrue(moduleClassLoaderOverlays.containsClass("a.b.c.TestClass"))
    assertTrue(moduleClassLoaderOverlays.containsClass("a.TestClass"))

    moduleClassLoaderOverlays.pushOverlayPath(tempOverlayPath2)
    assertTrue(moduleClassLoaderOverlays.containsClass("a.b.c.TestClass"))
    assertTrue(moduleClassLoaderOverlays.containsClass("a.TestClass"))
    assertTrue(moduleClassLoaderOverlays.containsClass("d.e.OtherTestClass"))
    assertTrue(moduleClassLoaderOverlays.containsClass("d.e.f.OtherTestClass"))
  }
}