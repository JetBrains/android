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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profiler.proto.Cpu.CpuTraceType
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.NativeNodeModel
import com.android.tools.profilers.cpu.nodemodel.SyscallModel
import com.google.common.annotations.VisibleForTesting

open class BaseCpuCapture @JvmOverloads constructor(/**
                                                     * ID of the trace used to generate the capture.
                                                     */
                                                    private val traceId: Long,
                                                    /**
                                                     * Technology used to generate the capture.
                                                     */
                                                    private val type: CpuTraceType,
                                                    /**
                                                     * Whether this trace supports dual clock timing info.
                                                     */
                                                    private val dualClock: Boolean,
                                                    /**
                                                     * User-facing string when this trace doesn't supports dual clock
                                                     */
                                                    private val dualClockMessage: String?,
                                                    range: Range,
                                                    captureTrees: Map<CpuThreadInfo, CaptureNode>,
                                                    private val tags: Set<String> = setOf()) : CpuCapture {
  @VisibleForTesting
  constructor(traceId: Long,
              type: CpuTraceType,
              range: Range,
              captureTrees: Map<CpuThreadInfo, CaptureNode>) :
    this(traceId, type, true, null, range, captureTrees) {
  }

  private val availableThreads: Set<CpuThreadInfo>
  private val threadIdToNode: Map<Int, CaptureNode>
  private val mainThreadId: Int
  private var clockType: ClockType
  private var tagsCollapsed = setOf<String>()
    private set
  private val unabbreviatedTrees: Map<CaptureNode, List<CaptureNode>>

  init {
    availableThreads = captureTrees.keys
    threadIdToNode = captureTrees.mapKeys { it.key.id }
    // If the trace is empty, use [NO_THREAD_ID].
    mainThreadId = (availableThreads.find { it.isMainThread } ?: captureTrees.maxByOrNull { it.value.duration }?.key)?.id ?: NO_THREAD_ID
    clockType = threadIdToNode[mainThreadId]?.clockType ?: ClockType.GLOBAL
    unabbreviatedTrees = threadIdToNode.values.associateWith { it.children.toList() }
  }

  companion object {
    /**
     * A placeholder thread ID when main thread doesn't exist.
     */
    const val NO_THREAD_ID = -1
  }

  /**
   * The CPU capture has its own [Timeline] for the purpose of exposing a variety of [Range]s.
   */
  private val timeline = DefaultTimeline().apply {
    dataRange.set(range)
    viewRange.set(range)
  }

  override fun getMainThreadId() = mainThreadId
  override fun getTimeline() = timeline
  override fun getCaptureNode(threadId: Int) = threadIdToNode[threadId]
  override fun getThreads() = availableThreads
  override fun getCaptureNodes() = threadIdToNode.values
  override fun containsThread(threadId: Int) = threadId in threadIdToNode
  override fun getTraceId() = traceId

  override fun updateClockType(clockType: ClockType) {
    // Avoid traversing the capture trees if there is no change.
    if (this.clockType != clockType) {
      this.clockType = clockType
      for (tree in captureNodes) {
        tree.descendantsStream.forEach { it.clockType = clockType }
      }
    }
  }

  override fun isDualClock() = dualClock
  override fun getDualClockDisabledMessage() = dualClockMessage
  override fun getType() = type

  override fun collapseNodesWithTags(tagsToCollapse: Set<String>) {
    if (tagsToCollapse != tagsCollapsed) {
      fun collapse(node: CaptureNode) = when (node.data.tag) {
        !in tagsToCollapse -> null
        else -> when (node.data) {
          is JavaMethodModel -> OpaqueJavaMethodModel
          is SyscallModel -> OpaqueSyscallModel
          else -> OpaqueNativeNodeModel
        }
      }
      fun isOpaqueModel(data: CaptureNodeModel) =
        data === OpaqueJavaMethodModel || data === OpaqueSyscallModel || data === OpaqueNativeNodeModel
      fun hideFromPaths(children: List<CaptureNode>) = children.map { it.abbreviatedBy(::collapse, ::isOpaqueModel) }

      unabbreviatedTrees.forEach { (root, unabbreviartedChildren) ->
        root.clearChildren()
        root.addChildren(if (tagsToCollapse.isEmpty()) unabbreviartedChildren else hideFromPaths(unabbreviartedChildren))
      }
      tagsCollapsed = tagsToCollapse
    }
  }

  override fun getTags() = tags
  override fun getCollapsedTags() = tagsCollapsed

  private object OpaqueJavaMethodModel : JavaMethodModel("<<java code>>", "", "")
  private object OpaqueSyscallModel: SyscallModel("<<syscall>>")
  private object OpaqueNativeNodeModel : NativeNodeModel() {
    init { myName = "<<native code>>" }
  }
}