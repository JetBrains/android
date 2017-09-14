/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw

import junit.framework.TestCase

import java.awt.*

class DrawIconTest : TestCase() {
  fun testSerialization() {
    var drawIcon = DrawIcon(Rectangle(10, 20, 100, 200), DrawIcon.IconType.DEEPLINK)
    val serialized = drawIcon.serialize()
    TestCase.assertEquals(serialized, "DrawIcon,23,10x20x100x200,DEEPLINK")

    drawIcon = DrawIcon(serialized)
    TestCase.assertEquals(serialized, drawIcon.serialize())
  }
}
