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

import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.TraceClassVisitor

class StringReplaceTestClass {
  val property = "PropertyValue"

  fun method() {
    println("MethodValue")
  }
}

class StringReplaceTransformTest {
  @Test
  fun testRenaming() {
    val testClassBytes = loadClassBytes(StringReplaceTestClass::class.java)

    val classReader = ClassReader(testClassBytes)
    val outputTrace = StringWriter()
    val classOutputWriter =
      TraceClassVisitor(ClassWriter(ClassWriter.COMPUTE_MAXS), PrintWriter(outputTrace))
    val repackageTransform =
      StringReplaceTransform(
        classOutputWriter,
        mapOf(
          StringReplaceTestClass::class.qualifiedName!! to
            mapOf("PropertyValue" to "RenamedPropertyValue", "MethodValue" to "RenamedMethodValue")
        ),
      )
    classReader.accept(repackageTransform, ClassReader.EXPAND_FRAMES)

    assertEquals(
      """
        LDC "RenamedPropertyValue"
        LDC "RenamedMethodValue"
      """
        .trimIndent(),
      outputTrace
        .toString()
        .lines()
        .filter { it.trimStart().startsWith("LDC") }
        .map { it.trim() }
        .joinToString("\n"),
    )
  }
}
