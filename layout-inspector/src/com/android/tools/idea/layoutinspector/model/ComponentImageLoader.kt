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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.layoutinspector.SkiaViewNode
import java.awt.Shape
import java.util.LinkedList

/**
 * Adds [DrawViewImage] corresponding to the images in the tree rooted at `skiaRoot` to the tree provided in the call to [loadImages].
 * The images added will be in the same order as in a depth-first traversal of `skiaRoot`, and in the normal case will be added to the
 * [ViewNode] with the same `drawId` as the `id` of the [SkiaViewNode]. If the order of nodes in the tree provided to [loadImages] and in
 * `skiaRoot` are different, images will be added to other nodes such that order is preserved.
 */
class ComponentImageLoader(
  private val nodeMap: Map<Long, ViewNode>, skiaRoot: SkiaViewNode
) {
  private val skiaNodes = LinkedList(skiaRoot.flatten().filter { it.image != null }.toList())
  private val checkedTreeIds = mutableSetOf<Long>()

  /**
   * Load images from skia parser
   */
  fun loadImages(window: AndroidWindow) {
    loadImages(window.root, window.deviceClip)
    window.skpLoadingComplete()
  }

  private fun loadImages(viewRoot: ViewNode, clip: Shape?) {
    ViewNode.writeAccess {
      viewRoot.drawChildren.clear()
      addImages(viewRoot, clip)
      var firstImage = skiaNodes.peek()
      viewRoot.children.forEach { child ->
        viewRoot.drawChildren.add(DrawViewChild(child))
        loadImages(child, clip)
        // If the child consumed any images, check again to see whether we can add.
        if (skiaNodes.size > 0 && skiaNodes[0] != firstImage) {
          addImages(viewRoot, clip)
          firstImage = skiaNodes.peek()
        }
      }
      checkedTreeIds.add(viewRoot.drawId)
    }
  }

  private fun ViewNode.WriteAccess.addImages(viewRoot: ViewNode, clip: Shape?) {
    while (skiaNodes.isNotEmpty() &&
           // The next image is drawn by this node, or some previous node but postponed until now.
           (skiaNodes.peek().id in checkedTreeIds.plus(viewRoot.drawId) ||
            // The next image is drawn by a node that we haven't encountered yet, but this node itself also draws, so we have to have the
            // next image draw first. We also have to make sure that the next image isn't drawn by one of our children, since maybe this
            // node is drawing after its children.
            // This should only happen when there's a structural mismatch between the ViewNodes and the SKP (which can happen due to the
            // way we build the tree in studio, and also potentially because of something happening on the device side).
            (skiaNodes.any { it.id == viewRoot.drawId } && viewRoot.flatten().none { it.drawId == skiaNodes.peek().id }))) {
      val skiaNode = skiaNodes.poll()
      val correspondingNode = nodeMap[skiaNode.id]
      viewRoot.drawChildren.add(DrawViewImage(skiaNode.image ?: continue, correspondingNode ?: continue, clip))
    }
  }
}