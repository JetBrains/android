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

import com.android.tools.idea.layoutinspector.model
import com.intellij.testFramework.UsefulTestCase
import org.junit.Test

class ViewNodeTest {
  @Test
  fun testFlatten() {
    val model = model {
      view(ROOT) {
        view(VIEW1) {
          view(VIEW3)
        }
        view(VIEW2)
      }
    }

    UsefulTestCase.assertSameElements(model[ROOT]!!.flatten().map { it.drawId }.toList(), ROOT, VIEW1, VIEW3, VIEW2)
    UsefulTestCase.assertSameElements(model[ROOT]!!.children[0].flatten().map { it.drawId }.toList(), VIEW1, VIEW3)
  }
}