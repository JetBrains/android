package com.android.tools.idea.rendering.classloading

import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter
import org.jetbrains.org.objectweb.asm.commons.Method
import kotlin.reflect.jvm.javaMethod

object CooperativeInterruptTransformLoopBreaker {
  @JvmStatic
  fun checkLoop() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
  }
}

object CooperativeInterruptTransformThreadLocalRandom {
  @JvmStatic
  fun nextInt(min: Int, max: Int): Int = java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max)
}

private fun java.lang.reflect.Method.toMethodType(): Method = Method(name, Type.getMethodDescriptor(this))

/**
 * Class transformation that inserts checks of [Thread#isInterrupted] into the loops, allowing the thread to be interrupted even in cases
 * where the code does not check for it explicitly.
 *
 * @param delegate the [ClassVisitor] to generate the output of this transformation.
 * @param checkPercentage the percentage in the [1, 100] range to do interrupt checks. The interrupt condition will only be checked in 1%
 * of the loops.
 * @param shouldInstrument callback that receives class and method name determines whether it should be transformed.
 */
class CooperativeInterruptTransform @JvmOverloads constructor(
  delegate: ClassVisitor,
  private val checkPercentage: Int = 1,
  private val shouldInstrument: (String, String) -> Boolean = { _, _ -> true }) :
  ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  init {
    if (checkPercentage !in 1..100) throw IllegalArgumentException("checkPercentage must be in [1, 100]")
  }

  override val uniqueId: String = "${CooperativeInterruptTransform::className},$checkPercentage,$shouldInstrument"
  private val loopBreakerType = Type.getType(CooperativeInterruptTransformLoopBreaker::class.java)
  private val loopCheckMethod = CooperativeInterruptTransformLoopBreaker::checkLoop.javaMethod!!.toMethodType()
  private val threadLocalRandomType = Type.getType(CooperativeInterruptTransformThreadLocalRandom::class.java)
  private val threadLocalRandomNextIntMethod = CooperativeInterruptTransformThreadLocalRandom::nextInt.javaMethod!!.toMethodType()
  private var className = ""

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
    className = name ?: ""
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(access: Int,
                           name: String?,
                           descriptor: String?,
                           signature: String?,
                           exceptions: Array<out String>?): MethodVisitor {
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
          ifICmp(GE, skipCheck)
          invokeStatic(loopBreakerType, loopCheckMethod)
          visitLabel(skipCheck)

          super.visitJumpInsn(opcode, label)
        }
      }
    }
    else {
      delegate
    }
  }
}