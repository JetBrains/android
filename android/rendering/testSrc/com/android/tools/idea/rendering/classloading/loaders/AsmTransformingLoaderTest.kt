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

import com.android.tools.idea.rendering.classloading.loadClassBytes
import com.android.tools.rendering.classloading.NopClassLocator
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.loaders.AsmTransformingLoader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import com.android.tools.rendering.classloading.loaders.NopLoader
import com.android.tools.rendering.classloading.loaders.StaticLoader
import org.junit.Assert.assertNull
import org.junit.Test
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter
import java.util.function.Function
import kotlin.test.assertEquals

private fun textifyClass(c: ByteArray): String {
  val stringWriter = StringWriter()
  ClassReader(c).accept(TraceClassVisitor(PrintWriter(stringWriter)), 0)

  return stringWriter.toString()
}

@Suppress("unused")
class TransformableClass {
  fun methodA() {}
  fun methodB() {}
}

class AsmTransformingLoaderTest {
  @Test
  fun `check transformation is applied`() {
    val methodRenamer = SimpleRemapper(
      "com/android/tools/idea/rendering/classloading/loaders/TransformableClass.methodA()V",
      "renamedMethodA")

    val staticLoader = StaticLoader(
      TransformableClass::class.java.name to loadClassBytes(TransformableClass::class.java)
    )

    val transformLoader = AsmTransformingLoader(
      ClassTransform(
        listOf(
          Function<ClassVisitor, ClassVisitor> { visitor -> ClassRemapper(visitor, methodRenamer) }
        )
      ),
      staticLoader,
      NopClassLocator)
    val transformedClass = transformLoader.loadClass(TransformableClass::class.java.name)
    assertEquals("""
      public final renamedMethodA()V
      public final methodB()V
      """.trimIndent(),
                 textifyClass(transformedClass!!)
                   .lines()
                   .map { it.trim() }
                   .filter {
                     it.contains("public final") && it.contains("method", true)
                   }
                   .joinToString("\n"))
  }

  @Test
  fun `check null classes do not throw exceptions`() {
    val transformLoader = AsmTransformingLoader(
      ClassTransform(
        listOf(
          Function<ClassVisitor, ClassVisitor> { throw IllegalStateException("Should not be called") }
        )
      ),
      NopLoader,
      NopClassLocator)
    assertNull(transformLoader.loadClass(TransformableClass::class.java.name))
  }
}