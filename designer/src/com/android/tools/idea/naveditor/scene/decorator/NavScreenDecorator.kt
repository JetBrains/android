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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.SdkConstants
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.naveditor.scene.draw.DrawScreenFrame
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.xml.XmlFile
import java.io.File
import java.util.concurrent.ExecutionException

/**
 * [SceneDecorator] responsible for creating draw commands for one screen/fragment/destination in the navigation editor.
 */
class NavScreenDecorator : SceneDecorator() {

  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
    val rect = Coordinates.getSwingRectDip(sceneContext, component.fillRect(null))
    list.add(DrawScreenFrame(rect, component.isSelected,
        component.drawState == SceneComponent.DrawState.HOVER || component.isDragging))
  }

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    super.addContent(list, time, sceneContext, component)

    val surface = sceneContext.surface ?: return
    val configuration = surface.configuration
    val facet = surface.model!!.facet

    val layout = component.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT) ?: return
    val fileName = configuration?.resourceResolver?.findResValue(layout, false)?.value ?: return
    val file = File(fileName)
    if (!file.exists()) {
      return
    }
    val manager = ThumbnailManager.getInstance(facet)
    val virtualFile = VfsUtil.findFileByIoFile(file, false) ?: return
    val psiFile = AndroidPsiUtils.getPsiFileSafely(surface.project, virtualFile) as? XmlFile ?: return
    val thumbnail = manager.getThumbnail(psiFile, surface, configuration) ?: return
    val image = try {
      // TODO: show progress icon during image creation
      thumbnail.get()
    }
    catch (ignore: InterruptedException) {
      // Shouldn't happen
      return
    }
    catch (ignore: ExecutionException) {
      return
    }

    list.add(DrawNavScreen(sceneContext.getSwingXDip(component.drawX.toFloat()) + 1,
        sceneContext.getSwingYDip(component.drawY.toFloat()) + 1,
        sceneContext.getSwingDimensionDip(component.drawWidth.toFloat()) - 1,
        sceneContext.getSwingDimensionDip(component.drawHeight.toFloat()) - 1,
        image))
  }

  override fun buildList(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    val displayList = DisplayList()
    super.buildList(displayList, time, sceneContext, component)
    list.add(NavigationDecorator.createDrawCommand(displayList, component))
  }
}
