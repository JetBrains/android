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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.actionSystem.AnActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class AnimationSpeedActionGroupTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Test
  fun testChangingSpeed() {
    val callback = Mockito.mock(MyCallback::class.java)

    val group = AnimationSpeedActionGroup(callback)
    val speedActions = group.getChildren(null)

    for (speed in PlaySpeed.values()) {
      val action = speedActions.single { it.templateText == speed.displayName }
      action.actionPerformed(Mockito.mock(AnActionEvent::class.java))
      Mockito.verify(callback).invoke(speed.speedFactor)
    }
  }

  private interface MyCallback : (Double) -> Unit
}
