/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.skia

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.layoutinspector.proto.SkiaParser.RequestedNodeInfo
import com.android.tools.layoutinspector.InvalidPictureException
import com.android.tools.layoutinspector.LayoutInspectorUtils.buildTree
import com.android.tools.layoutinspector.SkiaViewNode

/**
 * Service for converting a serialized `SkPicture` into a tree of [SkiaViewNode]s with rendered images.
 */
interface SkiaParser {
  /**
   * Convert a serialized `SkPicture` into a tree of [SkiaViewNode]s with rendered images.
   *
   * @param requestedNodes Only the `RenderNode`s in the `SkPicture` with IDs matching those given here will have corresponding
   *        [SkiaViewNode]s created. Rendering done by a `RenderNode` not included in this list will be included in the rendering of the
   *        first ancestor that is in the list.
   * @param scale Factor by which the rendered images should be scaled. Should probably be between 0 and 1.
   * @param isInterrupted Returns `true` if we should immediately cancel any pending operation.
   */
  @Throws(InvalidPictureException::class)
  fun getViewTree(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double,
    isInterrupted: () -> Boolean = { false }
  ): SkiaViewNode

  /**
   * Perform any necessary cleanup.
   */
  fun shutdown()
}

class SkiaParserImpl(
  private val failureCallback: () -> Unit,
  private val connectionFactory: SkiaParserServerConnectionFactory = SkiaParserServerConnectionFactoryImpl
) : SkiaParser {

  private val connectionSync = Any()

  @GuardedBy("connectionSync")
  private var connection: SkiaParserServerConnection? = null

  @Slow
  @Throws(InvalidPictureException::class)
  override fun getViewTree(
    data: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    scale: Double,
    isInterrupted: () -> Boolean
  ): SkiaViewNode {
    try {
      val (root, images) = synchronized (connectionSync) {
        if (connection == null) {
          connection = connectionFactory.createConnection(data)
        }
        connection?.getViewTree(data, requestedNodes, scale) ?: throw Exception("connection can't be null here")
      }
      return buildTree(root, images, isInterrupted, requestedNodes.associateBy { req -> req.id }) ?: throw ParsingFailedException()
    }
    catch (e: Exception) {
      failureCallback()
      throw e
    }
  }

  @Slow
  override fun shutdown() {
    synchronized (connectionSync) {
      connection?.shutdown()
      connection = null
    }
  }
}
