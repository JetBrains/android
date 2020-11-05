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

import com.android.testutils.MockitoKt
import com.android.tools.idea.layoutinspector.util.CheckUtil.assertDrawTreesEqual
import com.android.tools.idea.layoutinspector.view
import org.junit.Test
import java.awt.image.BufferedImage

class ComponentImageLoaderTest {

  // Test that the draw tree (that is, the tree via drawChildren) is correct when the initial view tree has intermediate or leaf nodes that
  // are not present in the skia tree.
  @Test
  fun testTeeWithExtraViewNodes() {
    val image1: BufferedImage = MockitoKt.mock()
    val image2: BufferedImage = MockitoKt.mock()
    val image4: BufferedImage = MockitoKt.mock()

    val skiaRoot = SkiaViewNode(1, listOf(
      SkiaViewNode(1, image1),
      SkiaViewNode(2, listOf(
        SkiaViewNode(2, image2)
      )),
      SkiaViewNode(4, listOf(
        SkiaViewNode(4, image4)
      ))
    ))

    val root = view(1L) {
      view(3L) {
        view(4L)
      }
      view(2L) {
        view(5L)
      }
    }
    // The model builder adds the draw children automatically, which we don't want to be populated yet in this case.
    ViewNode.writeDrawChildren { drawChildren ->
      root.flatten().forEach { it.drawChildren().clear() }
      ComponentImageLoader(root.flatten().associateBy { it.drawId }, skiaRoot).loadImages(drawChildren)
    }

    val expected = view(1L) {
      image(image1)
      view(2L) {
        image(image2)
        view(5L)
      }
      view(3L) {
        view(4L) {
          image(image4)
        }
      }
    }

    assertDrawTreesEqual(expected, root)
  }
}