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
package com.android.tools.idea.assistant.view

import java.net.URL
import junit.framework.TestCase

class LocalHTMLTest : TestCase() {
  var html =
    """
      <html>
      <body>
      <img src="anotherImage.png"/>
      <img src="/image.png" />
      <img src="/folder/image2.png" />
      </body>
      </html>
    """
      .trimIndent()

  fun testFindLocalImage() {
    val localImage = UIUtils.findLocalImage(html)
    assertEquals("image.png", localImage)
  }

  fun testReplaceLocalImage() {
    val localImage = UIUtils.findLocalImage(html)
    val url = URL("file:///test/$localImage")
    val processed = localImage?.let { UIUtils.addLocalHTMLPaths(html, url, it) }
    assertEquals(
      """
      <html>
      <body>
      <img src="anotherImage.png"/>
      <img src="file:/test/image.png" />
      <img src="file:/test/folder/image2.png" />
      </body>
      </html>
    """
        .trimIndent(),
      processed
    )
  }
}
