/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.scene.target

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.uibuilder.graphics.NlIcon
import icons.StudioIcons
import java.awt.Point
import javax.swing.JComponent

/**
 * An [ActionTarget] that displays a popup displaying a [JComponent]
 * provided by [componentProvider].
 *
 * @param closedCallback callback that will be called if the popup is properly closed
 * @param cancelCallBack callback that will be called if the popup is canceled
 * @param beforeShownCallback callback that will be called before the popup is shown
 */
class PopupActionTarget(
  private val componentProvider: () -> JComponent,
  closedCallback: (() -> Unit)? = null,
  cancelCallBack: (() -> Unit)? = null,
  beforeShownCallback: (() -> Unit)? = null
) : ActionTarget(null, NlIcon(StudioIcons.Menu.MENU, StudioIcons.Menu.MENU), null) {

  private var popup: LightCalloutPopup = LightCalloutPopup(closedCallback, cancelCallBack, beforeShownCallback)

  override fun mouseRelease(x: Int, y: Int, closestTargets: MutableList<Target>) {
    val designSurface = component.scene.designSurface
    val context = SceneContext.get(designSurface.currentSceneView)
    val position = Point(context.getSwingXDip(centerX), context.getSwingYDip(myTop))
    popup.show(componentProvider(), designSurface, position)
  }
}