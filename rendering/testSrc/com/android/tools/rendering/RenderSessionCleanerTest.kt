/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering

import com.android.ide.common.rendering.api.RenderSession
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics
import com.android.tools.rendering.classloading.loadClassBytes
import com.android.tools.rendering.classloading.loaders.StaticLoader
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("unused")
class FakeAndroidComposeView {
  companion object {
    @JvmField var systemPropertiesClass: Class<*>? = String::class.java

    @JvmField var getBooleanMethod: Any? = "dummyMethod"

    @JvmField val cacheList: MutableList<String> = mutableListOf("item1", "item2")

    @JvmField val cacheMap: MutableMap<String, String> = mutableMapOf("key" to "value")
  }
}

private class TestModuleClassLoader(
  parent: ClassLoader?,
  definedClasses: Map<String, ByteArray>,
  private val loadedClasses: Set<String> = definedClasses.keys,
) : ModuleClassLoader(parent, StaticLoader(definedClasses)) {
  override val stats: ModuleClassLoaderDiagnosticsRead = NopModuleClassLoadedDiagnostics
  override val isDisposed: Boolean = false

  override fun isCompatibleParentClassLoader(parent: ClassLoader?): Boolean = true

  override fun areDependenciesUpToDate(): Boolean = true

  override val isUserCodeUpToDate: Boolean = true

  override fun hasLoadedClass(fqcn: String): Boolean = fqcn in loadedClasses

  override val projectLoadedClasses: Set<String> = loadedClasses
  override val nonProjectLoadedClasses: Set<String> = emptySet()
  override val projectClassesTransform: ClassTransform = ClassTransform.identity
  override val nonProjectClassesTransform: ClassTransform = ClassTransform.identity

  override fun dispose() {}
}

@Suppress("DEPRECATION")
private fun createTestDefinedClasses(classDefinitions: Map<String, Class<*>>): Map<String, ByteArray> {
  val classNameRemapper =
    SimpleRemapper(classDefinitions.map { (newClassName, clazz) -> Type.getInternalName(clazz) to newClassName.replace('.', '/') }.toMap())
  return classDefinitions
    .map { (newClassName, clazz) ->
      val testClassBytes = loadClassBytes(clazz)
      val classReader = ClassReader(testClassBytes)
      val classOutputWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
      val remapper = ClassRemapper(classOutputWriter, classNameRemapper)
      classReader.accept(remapper, ClassReader.EXPAND_FRAMES)
      newClassName to classOutputWriter.toByteArray()
    }
    .toMap()
}

class RenderSessionCleanerTest {

  @Test
  fun testDisposeClearsAndroidComposeViewStaticFields() {
    val fqn = "androidx.compose.ui.platform.AndroidComposeView"
    val companionFqn = "androidx.compose.ui.platform.AndroidComposeView" + '$' + "Companion"

    val definedClasses =
      createTestDefinedClasses(
        mapOf(fqn to FakeAndroidComposeView::class.java, companionFqn to FakeAndroidComposeView.Companion::class.java)
      )

    val moduleClassLoader = TestModuleClassLoader(FakeAndroidComposeView::class.java.classLoader, definedClasses)

    val composeViewClass = moduleClassLoader.loadClass(fqn)
    moduleClassLoader.loadClass(companionFqn)

    // Verify initial values before dispose
    val sysPropField = composeViewClass.getDeclaredField("systemPropertiesClass").apply { isAccessible = true }
    val boolMethodField = composeViewClass.getDeclaredField("getBooleanMethod").apply { isAccessible = true }
    val cacheListField = composeViewClass.getDeclaredField("cacheList").apply { isAccessible = true }
    val cacheMapField = composeViewClass.getDeclaredField("cacheMap").apply { isAccessible = true }

    assertEquals(String::class.java, sysPropField.get(null))
    assertEquals("dummyMethod", boolMethodField.get(null))
    assertEquals(listOf("item1", "item2"), cacheListField.get(null) as List<*>)
    assertEquals(mapOf("key" to "value"), cacheMapField.get(null) as Map<*, *>)

    // Execute RenderSession.dispose
    val dummySession = object : RenderSession() {}
    dummySession.dispose(moduleClassLoader)

    // Verify static fields and collections were cleared
    assertNull(sysPropField.get(null))
    assertNull(boolMethodField.get(null))
    assertTrue((cacheListField.get(null) as List<*>).isEmpty())
    assertTrue((cacheMapField.get(null) as Map<*, *>).isEmpty())
  }

  @Test
  fun testDisposeSkipsAndroidComposeViewIfLoadedByParentClassLoader() {
    val fqn = "androidx.compose.ui.platform.AndroidComposeView"
    val parentClassLoader = FakeAndroidComposeView::class.java.classLoader
    val moduleClassLoader = TestModuleClassLoader(parentClassLoader, emptyMap(), setOf(fqn))

    FakeAndroidComposeView.systemPropertiesClass = String::class.java

    val dummySession = object : RenderSession() {}
    dummySession.dispose(moduleClassLoader)

    assertEquals(String::class.java, FakeAndroidComposeView.systemPropertiesClass)
  }
}
