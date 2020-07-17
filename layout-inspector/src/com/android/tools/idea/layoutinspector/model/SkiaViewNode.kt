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
class SkiaViewNode private constructor(val id: Long, var image: Image?, val children: List<SkiaViewNode>) {

  constructor(id: Long, children: List<SkiaViewNode> = listOf()) : this(id, null, children)

  constructor(id: Long, image: Image) : this(id, image, listOf())

  var imageGenerationTime: Long? = null

  fun flatten(): Sequence<SkiaViewNode> {
    return children.asSequence().flatMap { it.flatten() }.plus(this)
  }
}