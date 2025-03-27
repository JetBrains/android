/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.diagnostics.DiagnosticUtils
import java.lang.management.MonitorInfo
import java.lang.management.ThreadInfo
import kotlin.math.max

class FreezeGraph(val nodes: List<ThreadNode>, val awtNode: ThreadNode?) {

  private val idToNodeMap = nodes.associateBy { it.threadInfo.threadId }
  var awtDeadlocked = false
    private set
  var summary = ""
    private set

  enum class Confidence {
    LOW,
    HIGH
  }

  data class ThreadNode(val threadInfo: ThreadInfo) {
    val blockedBy: MutableList<BlockedEdge> = ArrayList()
    val blocking: MutableList<BlockedEdge> = ArrayList()

    fun getThreadNameAndId(): String = Companion.getThreadNameAndId(threadInfo)
  }

  fun addEdge(blockedNode: ThreadNode,
              blockingNode: ThreadNode,
              reason: String,
              timed: Boolean,
              confidence: Confidence) {
    val edge = BlockedEdge(blockedNode, blockingNode, reason, timed, confidence)
    blockedNode.blockedBy.add(edge)
    blockingNode.blocking.add(edge)
  }

  data class BlockedEdge(val blockedNode: ThreadNode,
                         val blockingNode: ThreadNode,
                         val reason: String,
                         val timed: Boolean,
                         val confidence: Confidence)

  companion object {
    fun analyzeThreads(threads: Array<ThreadInfo>): FreezeGraph {
      val threadNodes = threads.map { ThreadNode(it) }.toList()
      val graph = FreezeGraph(threadNodes, threadNodes.firstOrNull { DiagnosticUtils.isAwtThread(it.threadInfo) })

      graph.addEdgesFromLockInfo()
      graph.addEdgesFromReaderWriterLock()
      graph.addEdgesFromSemaphore()
      graph.analyze()
      return graph
    }

    fun getThreadNameAndId(info: ThreadInfo): String =
      "[\"${info.threadName}\" Id=${info.threadId}]"
  }

  private fun addEdgesFromLockInfo() {
    for (node in nodes) {
      val info = node.threadInfo
      val lockOwnerId = info.lockOwnerId
      if (lockOwnerId != -1L) {
        val blockingNode = idToNodeMap[lockOwnerId] ?: continue
        val lockInfo = info.lockInfo
        val reason = if (lockInfo is MonitorInfo)
          "${info.threadState} on monitor ${lockInfo.className}@${lockInfo.identityHashCode} owned by ${blockingNode.getThreadNameAndId()}"
        else
          "${info.threadState} on lock ${lockInfo.className}@${lockInfo.identityHashCode} owned by ${blockingNode.getThreadNameAndId()}"
        addEdge(node, blockingNode, reason, info.threadState == Thread.State.TIMED_WAITING, Confidence.HIGH)
      }
    }
  }

  private fun addEdgesFromReaderWriterLock() {
    // Find writer
    var writerNode: ThreadNode? = null
    for (node in nodes) {
      val threadInfo = node.threadInfo
      val threadState = threadInfo.threadState
      if (threadState != Thread.State.WAITING &&
          threadState != Thread.State.TIMED_WAITING) {
        continue
      }
      val stackTrace = threadInfo.stackTrace
      val match = stackTrace.indexOfLast {
        matchMethod(it, "com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runWriteAction")
      }
      if (match != -1) {
        writerNode = node
        break
      }
    }
    if (writerNode == null) {
      // No blocked writer thread
      return
    }
    val readersBlockingWriter = ArrayList<ThreadNode>()
    val readersBlockedByWriter = ArrayList<ThreadNode>()

    // Find readers
    for (node in nodes) {
      if (node == writerNode) continue
      val threadInfo = node.threadInfo
      val stackTrace = threadInfo.stackTrace
      val matchReader = max(
        stackTrace.indexOfLast { matchMethod(it, "com.intellij.openapi.application.impl.ApplicationImpl.runReadAction") },
        stackTrace.indexOfLast { matchMethod(it, "com.intellij.openapi.application.impl.ApplicationImpl.tryRunReadAction") })
      val matchWaitingForWriter = max(
        stackTrace.indexOfLast { matchMethod(it, "com.intellij.openapi.application.impl.ReadMostlyRWLock.startRead") },
        stackTrace.indexOfLast { matchMethod(it, "com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getReadPermit") })

      if (matchReader != -1) {
        val threadState = threadInfo.threadState
        if (matchWaitingForWriter != -1 && (threadState == Thread.State.WAITING ||
                                            threadState == Thread.State.TIMED_WAITING)) {
          readersBlockedByWriter.add(node)
        }
        else {
          readersBlockingWriter.add(node)
        }
      }
    }
    for (node in readersBlockedByWriter) {
      addEdge(node, writerNode, "Reader ${node.threadInfo.threadState} for writer ${writerNode.getThreadNameAndId()}", false,
              Confidence.HIGH)
    }
    for (node in readersBlockingWriter) {
      addEdge(writerNode, node, "Writer ${writerNode.threadInfo.threadState} for reader ${node.getThreadNameAndId()}", false,
              Confidence.HIGH)
    }
  }

  private fun addEdgesFromSemaphore() {
    for (node in nodes) {
      val threadInfo = node.threadInfo
      val threadState = threadInfo.threadState
      if (threadState != Thread.State.WAITING &&
          threadState != Thread.State.TIMED_WAITING) {
        continue
      }
      val stackTrace = threadInfo.stackTrace
      val match1 = stackTrace.indexOfLast { matchMethod(it, "java.util.concurrent.Semaphore.acquire") }
      if (match1 == -1 || match1 == stackTrace.size - 1)
        continue
      // Find other threads that might block this. Any thread within same class as the one calling acquire is considered.
      val previousFrame = stackTrace[match1 + 1]
      val className = getOuterClassName(previousFrame)

      var foundThread: ThreadNode? = null
      for (node2 in nodes) {
        if (node == node2) continue
        val threadInfo2 = node2.threadInfo
        val stackTrace2 = threadInfo2.stackTrace
        val match2 = stackTrace2.find { getOuterClassName(it) == className } ?: continue
        if (DiagnosticUtils.isAwtThread(threadInfo2)) {
          // AWT thread is considered only if there are no other background threads with frames with this class
          if (foundThread == null)
            foundThread = node2
        }
        else {
          foundThread = node2
          break
        }
      }
      foundThread?.let {
        addEdge(node, foundThread,
                "${node.threadInfo.threadState} for semaphore release ($className), possibly by ${foundThread.getThreadNameAndId()}",
                threadInfo.threadState == Thread.State.TIMED_WAITING, Confidence.LOW)
      }
    }
  }

  private fun getOuterClassName(frame: StackTraceElement) = frame.className.substringBefore('$')

  private fun matchMethod(it: StackTraceElement, classAndMethod: String) =
    (it.className + "." + it.methodName) == classAndMethod

  private fun analyze() {
    val sb = StringBuilder()
    awtDeadlocked = false
    sb.appendLine("THREAD LOCK INFO SECTION:")
    val awtNodeLocal = awtNode
    if (awtNodeLocal == null) {
      sb.appendLine("No AWT thread.")
      return
    }
    sb.appendLine("Lock path for AWT thread ${awtNodeLocal.getThreadNameAndId()}:")
    var currentNode = awtNodeLocal
    val visitedNodes = mutableSetOf<ThreadNode>()
    var hasTimeout = false
    while (currentNode != null) {
      visitedNodes.add(currentNode)
      val edge = currentNode.blockedBy.maxByOrNull { it.confidence }
      if (edge != null) {
        hasTimeout = hasTimeout or (edge.timed)
        sb.appendLine(" * ${currentNode.getThreadNameAndId()} ${edge.reason} (Confidence: ${edge.confidence})")
        val nextNode = edge.blockingNode
        if (visitedNodes.contains(nextNode)) {
          val deadlockString = if (hasTimeout) "TIMED DEADLOCK" else "DEADLOCK"
          sb.appendLine(" * $deadlockString while waiting for ${nextNode.getThreadNameAndId()}")
          awtDeadlocked = true
          break
        }
        currentNode = nextNode
      }
      else {
        val stackTrace = currentNode.threadInfo.stackTrace
        if (stackTrace.isNotEmpty()) {
          sb.appendLine("  ${currentNode.getThreadNameAndId()} - ${currentNode.threadInfo.threadState} at ${stackTrace[0]}")
        }
        break
      }
    }
    summary = sb.toString()
  }
}