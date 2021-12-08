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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.profiler.proto.Memory.AllocationStack.FrameCase
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.Classifier.Companion.of
import com.google.common.base.Strings

/**
 * Classifies [InstanceObject]s based on a particular stack trace line of its allocation stack. If the end of the stack is reached or
 * if there's no stack, then the instances are classified under [ClassSet.ClassClassifier]s.
 */
class MethodSet(private val captureObject: CaptureObject, private val methodInfo: MethodSetInfo, private val callstackDepth: Int) :
  ClassifierSet({ methodInfo.name }) {

  val className: String get() = methodInfo.className
  val methodName: String get() = methodInfo.methodName

  public override fun createSubClassifier(): Classifier = methodClassifier(captureObject, callstackDepth)

  sealed class MethodSetInfo {
    data class ByName(override val className: String, override val methodName: String): MethodSetInfo()
    data class ById(private val captureObject: CaptureObject, private val methodId: Long): MethodSetInfo() {
      override val className get() = classAndMethodName.first
      override val methodName get() = classAndMethodName.second
      private val classAndMethodName by lazy {
        val frameInfo = captureObject.getStackFrame(methodId)!!
        frameInfo.className to frameInfo.methodName
      }
    }
    abstract val className: String
    abstract val methodName: String
    val name: String get() = "$methodName()${if (Strings.isNullOrEmpty(className)) "" else " ($className)"}"
  }

  companion object {
    @JvmStatic
    fun createDefaultClassifier(captureObject: CaptureObject): Classifier = methodClassifier(captureObject, 0)

    private fun methodClassifier(captureObject: CaptureObject, depth: Int) =
      Classifier.Join(getMethodInfo(captureObject, depth), { MethodSet(captureObject, it, depth + 1) },
                      of(InstanceObject::getClassEntry, ::ClassSet))

    private fun getMethodInfo(captureObject: CaptureObject, depth: Int): (InstanceObject) -> MethodSetInfo? = { inst ->
      val stackDepth = inst.callStackDepth
      when {
        stackDepth <= 0 || depth >= stackDepth -> null
        else -> {
          val frameIndex = stackDepth - depth - 1
          val stack = inst.allocationCallStack
          when {
            stack == null -> {
              val location = inst.allocationCodeLocations[frameIndex]
              MethodSetInfo.ByName(Strings.nullToEmpty(location.className), Strings.nullToEmpty(location.methodName))
            }
            stack.frameCase == FrameCase.FULL_STACK -> {
              val stackFrame = stack.fullStack.getFrames(frameIndex)
              MethodSetInfo.ByName(stackFrame.className, stackFrame.methodName)
            }
            stack.frameCase == FrameCase.ENCODED_STACK -> {
              val smallFrame = stack.encodedStack.getFrames(frameIndex)
              MethodSetInfo.ById(captureObject, smallFrame.methodId)
            }
            else -> throw UnsupportedOperationException()
          }
        }
      }
    }
  }
}