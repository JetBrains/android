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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.utils.FileUtils.toSystemIndependentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.jetbrains.org.objectweb.asm.Type
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

  private val projectOverlayModificationCount: Long
    get() = ModuleClassLoaderOverlays.NotificationManager.getInstance(projectRule.project).modificationFlow.value

  private val moduleOverlayModificationCount: Long
    get() = ModuleClassLoaderOverlays.getInstance(projectRule.module).modificationTracker.modificationCount

  @Test
  fun `empty overlay does not return classes`() {
    assertNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))
  }

  @Test
  fun `invalidation updates the modification count`() {
    // Copy the classes into a temp directory to use as overlay
    val tempOverlayPath = Files.createTempDirectory("overlayTest")
    val packageDirPath = Files.createDirectories(tempOverlayPath.resolve(TestClass::class.java.packageName.replace(".", "/")))

    ModuleClassLoaderOverlays.getInstance(projectRule.module).pushOverlayPath(tempOverlayPath)
    val classFilePath = packageDirPath.resolve(TestClass::class.java.simpleName + ".class")
    Files.write(classFilePath, loadClassBytes(TestClass::class.java))
    assertNotNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))

    assertEquals(1, projectOverlayModificationCount)
    assertEquals(1, moduleOverlayModificationCount)
    ModuleClassLoaderOverlays.getInstance(projectRule.module).invalidateOverlayPaths()
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
    ModuleClassLoaderOverlays.getInstance(projectRule.module).pushOverlayPath(tempOverlayPath)
    assertNotNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))

    // If deleted, the class should disappear
    Files.delete(classFilePath)
    assertNull(ModuleClassLoaderOverlays.getInstance(projectRule.module).classLoaderLoader.loadClass(testClassName))
  }

  @Test
  fun `state loading`() {
    fun List<String>.asPlatformIndependent(): List<String> =
      map { toSystemIndependentPath(it) }

    val moduleClassLoaderOverlays = ModuleClassLoaderOverlays.getInstance(projectRule.module)

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
}