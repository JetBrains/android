package com.android.tools.idea.rendering.classloading

import com.android.tools.idea.rendering.RenderService
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter
import org.jetbrains.org.objectweb.asm.commons.Method
import kotlin.reflect.jvm.javaMethod

class TooManyAllocationsException(message: String) : RuntimeException(message)

/**
 * Static class invoked from user code to check the number of allocations per render action. Every new render action will increment
 * `RenderAsyncActionExecutor#executedRenderActionCount` so this class can check if a new action has started executing.
 */
object AllocationLimiterTransformChecker {
  private val accumulator = object : ThreadLocal<Long>() {
    override fun initialValue(): Long = 0L
  }
  private val lastRenderActionsExecutedCount = object : ThreadLocal<Long>() {
    override fun initialValue(): Long = 0L
  }

  @JvmStatic
  fun checkAllocation(maxAllocations: Long) {
    val lastRenderActionsCount = lastRenderActionsExecutedCount.get()
    val currentRenderActionsCount = RenderService.getRenderAsyncActionExecutor().executedRenderActionCount

    val accumulator = if (lastRenderActionsCount != currentRenderActionsCount) {
      // A new action has started, reset the counter
      this.lastRenderActionsExecutedCount.set(currentRenderActionsCount)
      0L
    }
    else accumulator.get()

    if (accumulator > maxAllocations)
      throw TooManyAllocationsException("$maxAllocations allocations exceeded in a single render action")

    this.accumulator.set(accumulator + 1)
  }
}

object AllocationLimiterTransformThreadLocalRandom {
  @JvmStatic
  fun nextInt(min: Int, max: Int): Int = java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max)
}

private fun java.lang.reflect.Method.toMethodType(): Method = Method(name, Type.getMethodDescriptor(this))

/**
 * Class transformation that inserts checks of the number of allocations in a single render action (see [RenderService]).
 *
 * @param delegate the [ClassVisitor] to generate the output of this transformation.
 * @param checkPercentage the percentage in the [1, 100] range to do interrupt checks. The interrupt condition will only be checked in 1%
 * of the loops.
 * @param maxAllocationsPerRenderAction number of allocations allowed in a single render action. If this number is exceeded, the user code
 * will throw [TooManyAllocationsException]. The allocations are sampled, so this number refers to the sampled number of allocations. If
 * `checkPercentage` is 1% and this parameter is 100, the number of actual allocations might reach 10_000.
 * @param shouldInstrument callback that receives class and method name determines whether it should be transformed.
 */
class RenderActionAllocationLimiterTransform @JvmOverloads constructor(
  delegate: ClassVisitor,
  private val checkPercentage: Int = 1,
  private val maxAllocationsPerRenderAction: Long = java.lang.Long.getLong("preview.allocation.limiter.max.threshold.count", 100_000),
  private val shouldInstrument: (String, String) -> Boolean = { className, _ -> !className.startsWith("androidx/") }) :
  ClassVisitor(Opcodes.ASM9, delegate), ClassVisitorUniqueIdProvider {
  init {
    if (checkPercentage !in 1..100) throw IllegalArgumentException("checkPercentage must be in [1, 100]")
  }

  override val uniqueId: String = "${RenderActionAllocationLimiterTransform::className},$checkPercentage,$shouldInstrument"
  private val allocationCheckerType = Type.getType(AllocationLimiterTransformChecker::class.java)
  private val allocationCheckMethod = AllocationLimiterTransformChecker::checkAllocation.javaMethod!!.toMethodType()
  private val threadLocalRandomType = Type.getType(AllocationLimiterTransformThreadLocalRandom::class.java)
  private val threadLocalRandomNextIntMethod = AllocationLimiterTransformThreadLocalRandom::nextInt.javaMethod!!.toMethodType()
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
        override fun visitTypeInsn(opcode: Int, type: String) {
          if (opcode == Opcodes.NEW) {
            val skipCheck = Label()
            // Min random value
            push(1)
            // Max random value
            push(100)
            invokeStatic(threadLocalRandomType, threadLocalRandomNextIntMethod)
            push(checkPercentage)
            ifICmp(GE, skipCheck)
            push(maxAllocationsPerRenderAction)
            invokeStatic(allocationCheckerType, allocationCheckMethod)
            visitLabel(skipCheck)
          }

          super.visitTypeInsn(opcode, type)
        }
      }
    }
    else {
      delegate
    }
  }
}