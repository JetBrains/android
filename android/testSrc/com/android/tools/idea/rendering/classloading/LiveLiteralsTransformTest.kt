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

import com.android.tools.idea.editors.literals.LiteralUsageReference
import com.android.tools.idea.run.util.StopWatch
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper
import org.jetbrains.org.objectweb.asm.commons.SimpleRemapper
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration

/**
 * Base interface for the test to log the constants.
 */
interface Receiver {
  fun receive(receiver: (Any?) -> Unit)
}

class TestClass : Receiver {
  private val a1 = "A1"
  val a2 = "A2" + "Added"

  // Small constants use ICONST
  private val a3 = 1 + 2
  private val a4 = 3f
  private val a5 = null

  private val arrayOfValues = arrayOf("AV" + "1", "AV2", "AV3", "AV4")

  override fun receive(receiver: (Any?) -> Unit) {
    val b1 = "b1"

    receiver(a1)
    receiver(a3)
    receiver(a4)
    receiver(a5)
    arrayOfValues.forEach { receiver(it) }

    receiver(b1)
    receiver(2000)
    receiver("Hello")
  }
}

class OuterTestClass {
  val oa1 = "OA1"
  val oa2 = 1024

  inner class InnerTestClass : Receiver {
    private val ia1 = "IA3"
    override fun receive(receiver: (Any?) -> Unit) {
      receiver(oa1)
      receiver(oa2)
      receiver(ia1)
      receiver("ia4")
      receiver(2000)
      receiver("Hello")
    }
  }
}

class LambdaTestClass : Receiver {
  override fun receive(receiver: (Any?) -> Unit) {
    val lambda = {
      "LAMBDA" + "VALUE"
    }
    receiver(lambda())
  }
}

class StaticTestClass : Receiver {
  override fun receive(receiver: (Any?) -> Unit) {
    StaticBase.staticCall(receiver)
  }

  object StaticBase {
    @JvmStatic
    fun staticCall(receiver: (Any?) -> Unit) {
      val s = "STATIC" + "VALUE"
      receiver(s)
    }
  }
}

class LiveLiteralsTransformTest {
  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  /** [StringWriter] all the constant accesses. */
  private val constantAccessTrace = StringWriter()
  private val constantAccessLogger = PrintWriter(constantAccessTrace)

  // This will will log to the stdout logging information that might be useful debugging failures.
  // The logging only happens if the test fails.
  @get:Rule
  val onFailureRule = object : TestWatcher() {
    override fun failed(e: Throwable?, description: Description?) {
      super.failed(e, description)

      println("---- Constant accesses ----")
      println(constantAccessTrace)
      println("\n---- All available keys ----")
      println(DefaultConstantRemapper.allKeysToText())
      println("\n---- Classes before transformation ----")
      println(beforeTransformTrace)
      println("\n---- Classes after transformation ----")
      println(afterTransformTrace)
    }
  }

  /**
   * Sets up a new [TestClassLoader].
   * We take the already compiled classes in the test project, and save it to a byte array, applying the
   * transformations.
   */
  private fun setupTestClassLoader(classDefinitions: Map<String, Class<*>>): TestClassLoader {
    // Create a SimpleRemapper that renames all the classes in `classDefinitions` from their old
    // names to the new ones.
    val classNameRemapper = SimpleRemapper(
      classDefinitions.map { (newClassName, clazz) -> Type.getInternalName(clazz) to newClassName }.toMap())
    val redefinedClasses = classDefinitions.map { (newClassName, clazz) ->
      val testClassBytes = loadClassBytes(clazz)

      val classReader = ClassReader(testClassBytes)
      val classOutputWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
      // Apply the live literals rewrite to all classes/methods
      val liveLiteralsRewriter = LiveLiteralsTransform(
        TraceClassVisitor(classOutputWriter, PrintWriter(afterTransformTrace))) { _, _ -> true }
      // Move the class
      val remapper = ClassRemapper(liveLiteralsRewriter, classNameRemapper)
      classReader.accept(TraceClassVisitor(remapper, PrintWriter(beforeTransformTrace)), ClassReader.EXPAND_FRAMES)

      newClassName to classOutputWriter.toByteArray()
    }.toMap()

    return TestClassLoader(LiveLiteralsTransformTest::class.java.classLoader,
                           redefinedClasses)
  }

  @Before
  fun setup() {
    ConstantRemapperManager.setRemapper(object : ConstantRemapper {
      override fun addConstant(classLoader: ClassLoader?, reference: LiteralUsageReference, initialValue: Any, newValue: Any) =
        DefaultConstantRemapper.addConstant(classLoader, reference, initialValue, newValue)

      override fun clearConstants(classLoader: ClassLoader?) = DefaultConstantRemapper.clearConstants(classLoader)

      override fun remapConstant(source: Any?, isStatic: Boolean, methodName: String, initialValue: Any?): Any? {
        val result = DefaultConstantRemapper.remapConstant(source, isStatic, methodName, initialValue)
        val classType = normalizeClassName(source?.let {
          if (isStatic)
            Type.getInternalName(it as Class<*>)
          else
            Type.getInternalName(it.javaClass)
        } ?: "<null>")
        constantAccessLogger.println("Access ($classType.$methodName, $initialValue) -> $result")
        return result
      }

      override fun getModificationCount(): Long = DefaultConstantRemapper.modificationCount
    })
  }

  @After
  fun tearDown() {
    ConstantRemapperManager.restoreDefaultRemapper()
  }

  private fun usageReference(className: String, method: String? = null): LiteralUsageReference {
    val normalizedMethodName = if (method.isNullOrEmpty())
      "" // Omit method if not passed. This can happen in lambda invocations, we omit "invoke".
    else
      ".$method"
    return LiteralUsageReference(
      FqName("${normalizeClassName(className)}$normalizedMethodName"),
      -1)
  }

  @Test
  fun `regular top class instrumented successfully`() {
    val testClassLoader = setupTestClassLoader(mapOf("Test" to TestClass::class.java))

    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference("Test", "<init>"), "A1", "Remapped A1")
    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference("Test", "<init>"), 3.0f, 90f)
    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference("Test", "<init>"), "AV1", "Remapped AV1")
    val newTestClassInstance = testClassLoader.load("Test").newInstance() as Receiver

    val constantOutput = StringBuilder()
    newTestClassInstance.receive {
      constantOutput.append(it).append('\n')
    }
    assertEquals("""
        Remapped A1
        3
        90.0
        null
        Remapped AV1
        AV2
        AV3
        AV4
        b1
        2000
        Hello

      """.trimIndent(),
                 constantOutput.toString())
  }

  @Test
  fun `check lambda is instrumented successfully`() {
    val lambdaClass = Class.forName("${LambdaTestClass::class.java.canonicalName}${'$'}receive${'$'}lambda${'$'}1")
    val testClassLoader = setupTestClassLoader(
      mapOf(
        "Test" to LambdaTestClass::class.java,
        "Test${'$'}receive${'$'}lambda${'$'}1" to lambdaClass
      ))

    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference("Test${'$'}receive${'$'}lambda${'$'}1"), "LAMBDAVALUE", "Remapped")
    val newTestClassInstance = testClassLoader.load("Test").newInstance() as Receiver

    val constantOutput = StringBuilder()
    newTestClassInstance.receive {
      constantOutput.append(it).append('\n')
    }
    assertEquals("""
        Remapped

      """.trimIndent(),
                 constantOutput.toString())
  }

  @Test
  fun `inner class is remapped successfully`() {
    val newOuterClassName = "OuterTestClass"
    val newInnerClassName = "$newOuterClassName${'$'}InnerTestClass"
    val testClassLoader = setupTestClassLoader(
      mapOf(
        newOuterClassName to OuterTestClass::class.java,
        newInnerClassName to OuterTestClass.InnerTestClass::class.java
      ))

    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference(newInnerClassName, "<init>"), "IA3", "Remapped IA3")
    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference(newOuterClassName, "<init>"), 1024, 4201)
    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference(newOuterClassName, "<init>"), "OA1", "Remapped OA1")
    val outerClassType = testClassLoader.load(newOuterClassName)
    val newOuterClassInstance = outerClassType.newInstance()
    val newTestClassInstance = testClassLoader.load(newInnerClassName)
      .getDeclaredConstructor(outerClassType)
      .newInstance(newOuterClassInstance) as Receiver

    val constantOutput = StringBuilder()
    newTestClassInstance.receive {
      constantOutput.append(it).append('\n')
    }
    assertEquals("""
        Remapped OA1
        4201
        Remapped IA3
        ia4
        2000
        Hello

      """.trimIndent(),
                 constantOutput.toString())

  }

  @Test
  fun `check static is instrumented successfully`() {
    val testClassLoader = setupTestClassLoader(
      mapOf(
        "Test" to StaticTestClass::class.java,
        "Test${'$'}StaticBase" to StaticTestClass.StaticBase::class.java
      ))

    DefaultConstantRemapper.addConstant(
      testClassLoader, usageReference("Test${'$'}StaticBase", "staticCall"), "STATICVALUE", "Remapped")
    val newTestClassInstance = testClassLoader.load("Test").newInstance() as Receiver

    val constantOutput = StringBuilder()
    newTestClassInstance.receive {
      constantOutput.append(it).append('\n')
    }
    assertEquals("""
        Remapped

      """.trimIndent(),
                 constantOutput.toString())
  }

  private fun time(callback: () -> Unit): Duration {
    val stopWatch = StopWatch()
    callback()
    return stopWatch.duration
  }

  @Test
  @Ignore("This test is just to measure performance locally and it's disabled by default")
  fun measurePerformance() {
    val iterations = 9500

    val originalClass = TestClass()
    println(time {
      repeat(iterations) {
        val builder = StringBuilder()
        originalClass.receive {
          builder.append(builder)
        }
      }
    })

    val testClassLoader = setupTestClassLoader(mapOf("Test" to TestClass::class.java))
    DefaultConstantRemapper.addConstant(testClassLoader, usageReference("Test", "<init>"), "A1", "Remapped A1")
    val newTestClassInstance = testClassLoader.load("Test").newInstance() as Receiver

    println(time {
      repeat(iterations) {
        val builder = StringBuilder()
        newTestClassInstance.receive {
          builder.append(builder)
        }
      }
    })
  }
}