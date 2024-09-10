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
package com.android.tools.idea.diagnostics.freeze

import com.android.tools.idea.diagnostics.util.ThreadCallTree

class ThreadCallTreeSorter(values: MutableCollection<ThreadCallTree>) {

  data class CallTreeWithPriority(val callTree: ThreadCallTree,
                                  val importance: Int,
                                  val treeDepth: Int)

  private val callTrees = values.map(::createCallTreeWithPriority)

  fun sort(): List<ThreadCallTree> {
    val sortedWith = callTrees.sortedWith(
      compareBy({ -it.importance }, { -it.treeDepth })
    )
    return sortedWith.map { it.callTree }
  }

  companion object {
    public fun createCallTreeWithPriority(callTree:ThreadCallTree): CallTreeWithPriority {
      val depth = callTree.computeMaxDepth()
      val importance: Int

      if (callTree.isAwtThread) {
        importance = 2
      } else {
        val hasRunReadAction: Boolean = callTree.exists { ste: StackTraceElement ->
          val methodCallString = ste.className + "." + ste.methodName
          methodCallString == "com.intellij.openapi.application.impl.ApplicationImpl.tryRunReadAction" || ste.methodName == "runReadAction"
        }
        val hasAcquireReadLock: Boolean = callTree.exists { ste: StackTraceElement ->
          val methodCallString = ste.className + "." + ste.methodName
          ste.methodName == "acquireReadLock" || methodCallString == "com.intellij.openapi.application.impl.ReadMostlyRWLock.startRead"
        }
        importance = if (!hasAcquireReadLock && hasRunReadAction) 1 else 0
      }
      return CallTreeWithPriority(callTree, importance, depth)
    }
  }
}