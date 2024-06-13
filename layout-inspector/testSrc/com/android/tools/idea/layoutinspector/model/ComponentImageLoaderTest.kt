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
import com.android.tools.idea.layoutinspector.window
import com.android.tools.layoutinspector.SkiaViewNode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.awt.image.BufferedImage

class ComponentImageLoaderTest {

  // Test that the draw tree (that is, the tree via drawChildren) is correct when the initial view
  // tree has intermediate or leaf nodes that
  // are not present in the skia tree.
  @Test
  fun testTreeWithExtraViewNodes() {
    val image1: BufferedImage = MockitoKt.mock()
    val image2: BufferedImage = MockitoKt.mock()
    val image4: BufferedImage = MockitoKt.mock()

    val skiaRoot =
      SkiaViewNode(
        1,
        listOf(
          SkiaViewNode(1, image1),
          SkiaViewNode(2, listOf(SkiaViewNode(2, image2))),
          SkiaViewNode(4, listOf(SkiaViewNode(4, image4))),
        ),
      )

    val window =
      window(1, 1L) {
        view(2L) { view(5L) }
        view(3L) { view(4L) }
      }
    val root = window.root
    // The model builder adds the draw children automatically, which we don't want to be populated
    // yet in this case.
    ViewNode.writeAccess {
      root.flatten().forEach { it.drawChildren.clear() }
      ComponentImageLoader(root.flatten().associateBy { it.drawId }, skiaRoot).loadImages(window)
    }

    val expected =
      view(1L) {
        image(image1)
        view(2L) {
          image(image2)
          view(5L)
        }
        view(3L) { view(4L) { image(image4) } }
      }

    assertDrawTreesEqual(expected, root)
  }

  // Test that if the view tree and skp tree are structurally different we still get images in the
  // expected order.
  @Test
  fun testViewSkpMismatch() {
    val image1: BufferedImage = MockitoKt.mock()
    val image2: BufferedImage = MockitoKt.mock()
    val image3: BufferedImage = MockitoKt.mock()
    val image4: BufferedImage = MockitoKt.mock()
    val image5: BufferedImage = MockitoKt.mock()
    val image6: BufferedImage = MockitoKt.mock()

    val skiaRoot =
      SkiaViewNode(
        1,
        listOf(
          SkiaViewNode(
            2,
            listOf(SkiaViewNode(2, image3), SkiaViewNode(6, listOf(SkiaViewNode(6, image6)))),
          ),
          SkiaViewNode(1, image1),
          SkiaViewNode(
            3,
            listOf(
              SkiaViewNode(4, listOf(SkiaViewNode(4, image4))),
              SkiaViewNode(1, image2),
              SkiaViewNode(5, listOf(SkiaViewNode(5, image5))),
            ),
          ),
        ),
      )

    val window =
      window(1, 1) {
        view(2)
        view(3) {
          view(4)
          view(5)
          view(6)
        }
      }
    val root = window.root

    ViewNode.writeAccess {
      root.flatten().forEach { it.drawChildren.clear() }
      ComponentImageLoader(root.flatten().associateBy { it.drawId }, skiaRoot).loadImages(window)
    }

    ViewNode.readAccess {
      // We don't really care that the images are in certain places in the tree, we just care that
      // they're in the right order.
      assertThat(
          root
            .preOrderFlatten()
            .toList()
            .flatMap { it.drawChildren }
            .filterIsInstance<DrawViewImage>()
            .map { it.image }
        )
        .containsExactlyElementsIn(listOf(image3, image6, image1, image4, image2, image5))
        .inOrder()
    }
  }
}
