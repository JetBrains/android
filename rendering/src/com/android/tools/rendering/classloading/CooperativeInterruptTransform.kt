/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.rendering.classloading

import kotlin.reflect.jvm.javaMethod
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter
import org.jetbrains.org.objectweb.asm.commons.Method

object CooperativeInterruptTransformLoopBreaker {
  @JvmStatic
  fun checkLoop() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
  }
}

object CooperativeInterruptTransformThreadLocalRandom {
  @JvmStatic
  fun nextInt(min: Int, max: Int): Int =
    java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max)
}

fun java.lang.reflect.Method.toMethodType(): Method = Method(name, Type.getMethodDescriptor(this))

/**
 * Class transformation that inserts checks of [Thread#isInterrupted] into the loops, allowing the
 * thread to be interrupted even in cases where the code does not check for it explicitly.
 *
 * @param delegate the [ClassVisitor] to generate the output of this transformation.
 * @param checkPercentage the percentage in the [1, 100] range to do interrupt checks. The interrupt
 *   condition will only be checked in 1% of the loops.
 * @param shouldInstrument callback that receives class and method name determines whether it should
 *   be transformed.
 */
class CooperativeInterruptTransform
@JvmOverloads
constructor(
  delegate: ClassVisitor,
  private val checkPercentage: Int = 1,
  private val shouldInstrument: (String, String) -> Boolean = { _, _ -> true },
) : ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  init {
    if (checkPercentage !in 1..100)
      throw IllegalArgumentException("checkPercentage must be in [1, 100]")
  }

  override val uniqueId: String =
    "${CooperativeInterruptTransform::className},$checkPercentage,$shouldInstrument"
  private val loopBreakerType = Type.getType(CooperativeInterruptTransformLoopBreaker::class.java)
  private val loopCheckMethod =
    CooperativeInterruptTransformLoopBreaker::checkLoop.javaMethod!!.toMethodType()
  private val threadLocalRandomType =
    Type.getType(CooperativeInterruptTransformThreadLocalRandom::class.java)
  private val threadLocalRandomNextIntMethod =
    CooperativeInterruptTransformThreadLocalRandom::nextInt.javaMethod!!.toMethodType()
  private var className = ""

  override fun visit(
    version: Int,
    access: Int,
    name: String?,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?,
  ) {
    className = name ?: ""
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor {
    val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
    return if (shouldInstrument(className, name ?: "")) {
      object : GeneratorAdapter(Opcodes.ASM9, delegate, access, name, descriptor) {
        override fun visitJumpInsn(opcode: Int, label: Label?) {
          val skipCheck = Label()
          // Min random value
          push(1)
          // Max random value
          push(100)
          invokeStatic(threadLocalRandomType, threadLocalRandomNextIntMethod)
          push(checkPercentage)
          ifICmp(GT, skipCheck)
          invokeStatic(loopBreakerType, loopCheckMethod)
          visitLabel(skipCheck)

          super.visitJumpInsn(opcode, label)
        }
      }
    } else {
      delegate
    }
  }
}
