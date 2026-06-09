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
package com.android.tools.preview

import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.NopModuleClassLoadedDiagnostics
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.security.RenderSandbox
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.io.File
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliciousProvider {
  init {
    // Attempt to write a file. This should be blocked by BasicRenderSandbox.
    val file = File(PreviewParameterSandboxTest.tempDir, "malicious_output.txt")
    file.createNewFile()
  }

  fun getCount(): Int = 1
}

class SandboxTestClassLoader(
  parent: ClassLoader,
  private val classDefinitions: Map<String, Class<*>>,
  private val transformFactory: (ClassVisitor) -> ClassVisitor,
) : ClassLoader(parent) {

  private val definitions = mutableMapOf<String, ByteArray>()

  init {
    val classNameRemapper =
      SimpleRemapper(
        classDefinitions.map { (newClassName, clazz) -> Type.getInternalName(clazz) to newClassName.replace('.', '/') }.toMap()
      )

    classDefinitions.forEach { (newClassName, clazz) ->
      val originalName = "${Type.getInternalName(clazz)}.class"
      val originalBytes = clazz.classLoader.getResourceAsStream(originalName)!!.use { it.readBytes() }

      val classReader = ClassReader(originalBytes)
      val classOutputWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
      val remapper = ClassRemapper(transformFactory(classOutputWriter), classNameRemapper)
      classReader.accept(remapper, ClassReader.EXPAND_FRAMES)

      definitions[newClassName] = classOutputWriter.toByteArray()
    }
  }

  override fun findClass(name: String): Class<*> {
    val bytes = definitions[name]
    if (bytes != null) {
      return defineClass(name, bytes, 0, bytes.size)
    }
    return super.findClass(name)
  }

  fun loadClassBytes(name: String): ByteArray {
    return definitions[name] ?: throw ClassNotFoundException(name)
  }
}

class TestModuleClassLoader(parent: ClassLoader?, private val testClassLoader: SandboxTestClassLoader) :
  ModuleClassLoader(
    parent,
    object : DelegatingClassLoader.Loader {
      override fun loadClass(fqcn: String): ByteArray? {
        return try {
          testClassLoader.loadClassBytes(fqcn)
        } catch (e: ClassNotFoundException) {
          null
        }
      }
    },
  ) {
  override val stats: ModuleClassLoaderDiagnosticsRead = NopModuleClassLoadedDiagnostics
  override val isDisposed = false

  override fun isCompatibleParentClassLoader(parent: ClassLoader?) = true

  override fun areDependenciesUpToDate() = true

  override val isUserCodeUpToDate = true

  override fun hasLoadedClass(fqcn: String) =
    try {
      testClassLoader.loadClass(fqcn) != null
    } catch (e: ClassNotFoundException) {
      false
    }

  override val projectLoadedClasses = emptySet<String>()
  override val nonProjectLoadedClasses = emptySet<String>()
  override val projectClassesTransform = ClassTransform.identity
  override val nonProjectClassesTransform = ClassTransform.identity

  override fun dispose() {}
}

class PreviewParameterSandboxTest {

  companion object {
    val tempDir = File(FileUtil.createTempDirectory("sandbox_test", null).canonicalPath)
  }

  @After
  fun tearDown() {
    FileUtil.delete(tempDir)
  }

  @Test
  fun testPreviewParameterProviderIsSandboxed() {
    val maliciousFile = File(tempDir, "malicious_output.txt")
    assertFalse(maliciousFile.exists())

    // Rename the class to force loading from TestClassLoader instead of parent
    val targetClassName = "com.android.tools.preview.SandboxedMaliciousProvider"

    // Set up the classloader that applies RenderSandbox transform
    val testClassLoader =
      SandboxTestClassLoader(this.javaClass.classLoader, mapOf(targetClassName to MaliciousProvider::class.java)) { visitor ->
        RenderSandbox.getClassTransform(visitor)
      }

    val basePreviewElement =
      SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
        methodFqn = "com.test.MyPreview",
        displaySettings =
          PreviewDisplaySettings(
            name = "MyPreview",
            baseName = "MyPreview",
            parameterName = null,
            group = null,
            showDecoration = false,
            background = PreviewDisplaySettings.Background.None,
            organizationGroup = "",
            organizationName = "",
          ),
        previewElementDefinition = null,
        previewBody = null,
        configuration = PreviewConfiguration.cleanAndGet(),
      )

    val parameter = PreviewParameter(name = "param", index = 0, providerClassFqn = targetClassName, limit = 1)

    val template =
      ParametrizedComposePreviewElementTemplate(
        basePreviewElement = basePreviewElement,
        parameterProviders = listOf(parameter),
        parentClassLoader = this.javaClass.classLoader,
        privateClassLoaderFactory = {
          RenderModelModule.ClassLoaderProvider { _, _, _, _ ->
            val moduleClassLoader = TestModuleClassLoader(this.javaClass.classLoader, testClassLoader)
            ModuleClassLoaderManager.Reference(moduleClassLoader) {}
          }
        },
      )

    val result = template.resolve().toList()

    // Since the provider execution should have failed with SecurityException,
    // it should have returned a single fake error instance.
    assertEquals(1, result.size)
    val firstInstance = result.first()
    assertTrue(firstInstance is SingleComposePreviewElementInstance)
    assertEquals("${targetClassName}.$FAKE_PREVIEW_PARAMETER_PROVIDER_METHOD", firstInstance.methodFqn)

    // Verify that the file was NOT created (write was blocked)
    assertFalse("File creation should have been blocked", maliciousFile.exists())
  }
}
