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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.idea.common.scene.draw.DrawCommand
import org.jetbrains.android.AndroidTestCase

fun assertDrawCommandsEqual(expected: DrawSelfAction, actual: DrawCommand) {
  val drawSelfAction = actual as DrawSelfAction

  AndroidTestCase.assertEquals(expected.rectangle, drawSelfAction.rectangle)
  AndroidTestCase.assertEquals(expected.scale, drawSelfAction.scale)
  AndroidTestCase.assertEquals(expected.color, drawSelfAction.color)
  AndroidTestCase.assertEquals(expected.isPopAction, drawSelfAction.isPopAction)
}

fun assertDrawCommandsEqual(expected: DrawAction, actual: DrawCommand) {
  val drawAction = actual as DrawAction

  AndroidTestCase.assertEquals(expected.source, drawAction.source)
  AndroidTestCase.assertEquals(expected.dest, drawAction.dest)
  AndroidTestCase.assertEquals(expected.scale, drawAction.scale)
  AndroidTestCase.assertEquals(expected.color, drawAction.color)
  AndroidTestCase.assertEquals(expected.isPopAction, drawAction.isPopAction)
}