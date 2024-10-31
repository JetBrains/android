/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.scene.Display
import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import org.junit.rules.ExternalResource

/**
 * JUnit rule that enabled the ability of rendering and waiting for the background [Display] renders
 * to complete. When using this rule, call [renderInFakeUi] to ensure the render is completed
 * correctly.
 */
class AsyncDisplayRule : ExternalResource() {
  private var previousCaptureRepaintsValue = false

  override fun before() {
    previousCaptureRepaintsValue = Display.setCaptureDisplayRepaints(true)
  }

  override fun after() {
    Display.setCaptureDisplayRepaints(previousCaptureRepaintsValue)
  }

  fun renderInFakeUi(fakeUi: FakeUi): BufferedImage {
    var render: BufferedImage
    do {
      UIUtil.invokeAndWaitIfNeeded { fakeUi.layoutAndDispatchEvents() }
      fakeUi.render()
      render = fakeUi.render()
    } while (Display.hasPendingPaints())
    return render
  }
}
