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
package com.android.tools.idea.compose.preview.analytics

import com.android.tools.idea.common.analytics.DesignerUsageTrackerManager
import com.android.tools.idea.common.analytics.setApplicationId
import com.android.tools.idea.preview.PreviewNode
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposeMultiPreviewEvent
import com.intellij.openapi.diagnostic.Logger
import java.util.Objects
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.jetbrains.android.facet.AndroidFacet

/** Usage tracker implementation for Compose MultiPreview. */
interface MultiPreviewUsageTracker {
  /**
   * Cache to store a hash value of the last MultiPreview graph logged for a given file. This data
   * is intended to be used to avoid logging a repeated Event when the graph hasn't changed.
   */
  val graphCache: Cache<String, Int>?
  fun logEvent(event: MultiPreviewEvent): AndroidStudioEvent.Builder

  companion object {
    private val NOP_TRACKER = MultiPreviewNopTracker()
    private val MANAGER =
      DesignerUsageTrackerManager(::InternalMultiPreviewUsageTracker, NOP_TRACKER)

    fun getInstance(facet: AndroidFacet?) = MANAGER.getInstance(facet)
  }
}

/**
 * Empty [MultiPreviewUsageTracker] implementation, used when the user is not opt-in or in tests.
 */
private class MultiPreviewNopTracker : MultiPreviewUsageTracker {
  override val graphCache: Cache<String, Int>? = null
  override fun logEvent(event: MultiPreviewEvent) = event.createAndroidStudioEvent()
}

/**
 * Default [MultiPreviewUsageTracker] implementation that sends the event to the analytics backend.
 */
private class InternalMultiPreviewUsageTracker(
  private val executor: Executor,
  private val facet: AndroidFacet?,
  private val studioEventTracker: Consumer<AndroidStudioEvent.Builder>
) : MultiPreviewUsageTracker {
  override val graphCache: Cache<String, Int> = CacheBuilder.newBuilder().build()

  override fun logEvent(event: MultiPreviewEvent): AndroidStudioEvent.Builder {
    val graphHashValue = event.hashCode()
    // When the MultiPreview graph hasn't changed, don't log a repeated entry
    if (graphCache.getIfPresent(event.fileFqName) == graphHashValue) {
      return event.createAndroidStudioEvent()
    }
    graphCache.put(event.fileFqName, graphHashValue)
    event.createAndroidStudioEvent().setApplicationId(facet).let {
      executor.execute { studioEventTracker.accept(it) }
      return it
    }
  }
}

/**
 * Represents a [ComposeMultiPreviewEvent] to be tracked, and uses the builder pattern to create it.
 */
class MultiPreviewEvent(private val nodes: List<MultiPreviewNode>, val fileFqName: String) {
  private val eventBuilder =
    ComposeMultiPreviewEvent.newBuilder()
      .addAllMultiPreviewNodes( // Don't log useless nor Preview nodes
        nodes
          .filter { !it.nodeInfo.isUseless() && !it.nodeInfo.isPreviewType() }
          .map { it.nodeInfo.build() }
      )

  fun build(): ComposeMultiPreviewEvent {
    return eventBuilder.build()
  }

  override fun hashCode(): Int {
    return Objects.hash(nodes.map { it.nodeInfo.hashCode() }.sorted(), fileFqName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    return other is MultiPreviewEvent &&
      javaClass == other.javaClass &&
      fileFqName == other.fileFqName &&
      nodes == other.nodes
  }
}

/** Creates and returns an [AndroidStudioEvent.Builder] from an [MultiPreviewEvent]. */
private fun MultiPreviewEvent.createAndroidStudioEvent(): AndroidStudioEvent.Builder {
  return AndroidStudioEvent.newBuilder()
    .setKind(AndroidStudioEvent.EventKind.COMPOSE_MULTI_PREVIEW)
    .setComposeMultiPreviewEvent(build())
}

/** Represents a node in the MultiPreview graph with analytics information */
interface MultiPreviewNode : PreviewNode {
  /** Analytics information about this node, as part of a MultiPreview graph */
  val nodeInfo: MultiPreviewNodeInfo
}

class MultiPreviewNodeImpl(override val nodeInfo: MultiPreviewNodeInfo) : MultiPreviewNode

/**
 * Represents a node for a [ComposeMultiPreviewEvent], and uses the builder pattern to create it.
 */
class MultiPreviewNodeInfo(type: ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType) {
  private val LOG = Logger.getInstance(MultiPreviewNodeInfo::class.java)
  private val nodeInfoBuilder =
    ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.newBuilder().setNodeType(type)

  /** True for nodes that don't contain any Preview in its whole DFS subtree */
  fun isUseless() = nodeInfoBuilder.subtreePreviewsCount == 0

  fun isPreviewType() =
    nodeInfoBuilder.nodeType ==
      ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.PREVIEW_NODE
  private fun isMultiPreviewType() =
    nodeInfoBuilder.nodeType ==
      ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.MULTIPREVIEW_NODE

  private fun isRootComposableType() =
    nodeInfoBuilder.nodeType ==
      ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo.NodeType.ROOT_COMPOSABLE_FUNCTION_NODE

  private fun clearCounters() {
    nodeInfoBuilder.clearPreviewChildsCount()
    nodeInfoBuilder.clearMultiPreviewChildsCount()
    nodeInfoBuilder.clearSubtreePreviewsCount()
    nodeInfoBuilder.clearSubtreeMultiPreviewsCount()
    nodeInfoBuilder.clearSubtreeUselessNodesCount()
  }

  /**
   * Set the counters using the information form this node's child nodes. Note: any node of a type
   * different that MultiPreview in [multiPreviewChildNodes] will be ignored.
   */
  fun withChildNodes(
    multiPreviewChildNodes: Collection<MultiPreviewNodeInfo?>,
    previewChildrenCount: Int
  ): MultiPreviewNodeInfo {
    if (!this.isMultiPreviewType() && !this.isRootComposableType()) {
      LOG.error(
        "Nodes of a type different that MultiPreview and RootComposable shouldn't have child nodes"
      )
      return this
    }

    // Reset the counters to 0
    clearCounters()

    // Ignore non-MultiPreview type nodes
    val childNodes = multiPreviewChildNodes.filterNotNull().filter { it.isMultiPreviewType() }

    nodeInfoBuilder.previewChildsCount = previewChildrenCount
    nodeInfoBuilder.subtreePreviewsCount = previewChildrenCount

    // Update the counters using childNodes information
    childNodes.forEach {
      if (!it.isUseless()) nodeInfoBuilder.multiPreviewChildsCount++
      nodeInfoBuilder.subtreePreviewsCount += it.nodeInfoBuilder.subtreePreviewsCount
      nodeInfoBuilder.subtreeMultiPreviewsCount += it.nodeInfoBuilder.subtreeMultiPreviewsCount
      nodeInfoBuilder.subtreeUselessNodesCount += it.nodeInfoBuilder.subtreeUselessNodesCount
    }

    if (this.isMultiPreviewType()) {
      if (this.isUseless()) nodeInfoBuilder.subtreeUselessNodesCount++
      else nodeInfoBuilder.subtreeMultiPreviewsCount++
    }
    return this
  }

  fun withDepthLevel(depthLevel: Int): MultiPreviewNodeInfo {
    nodeInfoBuilder.depthLevel = depthLevel
    return this
  }

  @Suppress("UnstableApiUsage")
  fun withComposableFqn(composableFqn: String): MultiPreviewNodeInfo {
    nodeInfoBuilder.anonymizedComposableId =
      Hashing.farmHashFingerprint64()
        .newHasher()
        .putString(composableFqn, Charsets.UTF_8)
        .hash()
        .asLong()
    return this
  }

  fun build(): ComposeMultiPreviewEvent.ComposeMultiPreviewNodeInfo {
    return nodeInfoBuilder.build()
  }

  override fun hashCode(): Int {
    return nodeInfoBuilder.build().hashCode()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    return other is MultiPreviewNodeInfo &&
      javaClass == other.javaClass &&
      nodeInfoBuilder.build() == other.nodeInfoBuilder.build()
  }
}
