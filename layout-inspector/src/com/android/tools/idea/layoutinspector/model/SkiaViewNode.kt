/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.awt.Image

/**
 * A view as seen in a Skia image.
 * We can only have children or an image, not both.
 *
 * TODO: maybe just use the proto coming from the parser directly instead of this class.
 */
class SkiaViewNode private constructor(
  val id: String,
  val type: String,
  var x: Int,
  var y: Int,
  var width: Int,
  var height: Int,
  var image: Image?,
  children: List<SkiaViewNode>
) {

  constructor(id: String,
              type: String,
              x: Int,
              y: Int,
              width: Int,
              height: Int,
              children: List<SkiaViewNode> = listOf()
  ) : this(id, type, x, y, width, height, null, children)

  constructor(id: String,
              type: String,
              x: Int,
              y: Int,
              width: Int,
              height: Int,
              image: Image
  ) : this(id, type, x, y, width, height, image, listOf())

  var imageGenerationTime: Long? = null

  /**
   * Map of View IDs to views.
   */
  val children: MutableList<SkiaViewNode> = mutableListOf()

  init {
    children.forEach { addChild(it) }
  }

  @Suppress("unused") // invoked via reflection
  fun addChild(child: SkiaViewNode) {
    children.add(child)
  }

  fun flatten(): Sequence<SkiaViewNode> {
    return children.asSequence().flatMap { it.flatten() }.plus(this)
  }
}