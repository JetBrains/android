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
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

private object TestClassLoadingUtils

internal fun loadClassBytes(c: Class<*>): ByteArray {
  val className = "${Type.getInternalName(c)}.class"
  c.classLoader.getResourceAsStream(className)!!.use { return it.readBytes() }
}

internal fun textifyClass(c: ByteArray): String {
  val stringWriter = StringWriter()
  ClassReader(c).accept(TraceClassVisitor(PrintWriter(stringWriter)), 0)

  return stringWriter.toString()
}

/**
 * Sets up a new [TestClassLoader].
 * We take the already compiled classes in the test project, and save it to a byte array, applying the
 * transformations.
 */
internal fun setupTestClassLoaderWithTransformation(
  classDefinitions: Map<String, Class<*>>,
  beforeTransformTrace: StringWriter,
  afterTransformTrace: StringWriter,
  classTransformation: (ClassVisitor) -> ClassVisitor
): TestClassLoader {
  // Create a SimpleRemapper that renames all the classes in `classDefinitions` from their old
  // names to the new ones.
  val classNameRemapper = SimpleRemapper(
    classDefinitions.map { (newClassName, clazz) -> Type.getInternalName(clazz) to newClassName }.toMap())
  val redefinedClasses = classDefinitions.map { (newClassName, clazz) ->
    val testClassBytes = loadClassBytes(clazz)

    val classReader = ClassReader(testClassBytes)
    val classOutputWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    // Move the class
    val remapper = ClassRemapper(classTransformation(TraceClassVisitor(classOutputWriter, PrintWriter(afterTransformTrace))),
                                 classNameRemapper)
    classReader.accept(TraceClassVisitor(remapper, PrintWriter(beforeTransformTrace)), ClassReader.EXPAND_FRAMES)

    newClassName to classOutputWriter.toByteArray()
  }.toMap()

  return TestClassLoader(TestClassLoadingUtils::class.java.classLoader,
                         redefinedClasses)
}