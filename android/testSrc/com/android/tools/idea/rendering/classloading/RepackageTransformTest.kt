/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading

import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.PrintWriter
import java.io.StringWriter

class TestClass

class ToBeRepackaged {
  fun method() {
    Class.forName("com.android.tools.idea.rendering.classloading.TestClass")
  }
}

class RepackageTransformTest {
  @Test
  fun testRepackaging() {
    val testClassBytes = loadClassBytes(ToBeRepackaged::class.java)

    val classReader = ClassReader(testClassBytes)
    val outputTrace = StringWriter()
    val classOutputWriter = TraceClassVisitor(ClassWriter(ClassWriter.COMPUTE_MAXS), PrintWriter(outputTrace))
    val repackageTransform = RepackageTransform(classOutputWriter, listOf("com.android.tools.idea.rendering.classloading."), "internal.test.")
    classReader.accept(repackageTransform, ClassReader.EXPAND_FRAMES)

    // Find all references to the class name and make sure they've been transformed.
    val referenceRegex = Regex("([a-z./]+com/android/tools/[a-z./]+)")

    assertEquals("internal/test/com/android/tools/idea/rendering/classloading/", referenceRegex.findAll(outputTrace.toString())
      .map { it.value }
      .distinct()
      .joinToString("\n"))

    assertEquals(
      """
        LDC "com.android.tools.idea.rendering.classloading.TestClass"
        LDC "internal.test.com.android.tools.idea.rendering.classloading.TestClass"
        INVOKESTATIC internal/test/com/android/tools/idea/rendering/classloading/ClassForNameHandler.forName (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Class;
      """.trimIndent(),
      outputTrace
        .toString()
        .lines()
        .filter {
          it.trimStart().startsWith("LDC") || it.trimStart().startsWith("INVOKESTATIC")
        }
        .map { it.trim() }
        .joinToString("\n")
    )
  }
}