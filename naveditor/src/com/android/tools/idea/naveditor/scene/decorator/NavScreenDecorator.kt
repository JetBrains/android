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
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.scene.DRAW_NAV_SCREEN_LEVEL
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.naveditor.scene.draw.DrawPlaceholder
import com.android.tools.idea.res.resolve
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.xml.XmlFile
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File

/**
 * [NavScreenDecorator] Base class for navigation decorators.
 */
abstract class NavScreenDecorator : NavBaseDecorator() {

  // TODO: Either set an appropriate clip here, or make this the default behavior in the base class
  override fun buildListChildren(list: DisplayList,
                                 time: Long,
                                 sceneContext: SceneContext,
                                 component: SceneComponent) {
    for (child in component.children) {
      child.buildDisplayList(time, list, sceneContext)
    }
  }

  protected fun buildImage(sceneContext: SceneContext,
                           component: SceneComponent,
                           @SwingCoordinate rectangle: Rectangle2D.Float): RefinableImage? {
    val layout = component.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT)
    val className = component.nlComponent.className

    if (layout == null && className == null) {
      return null
    }

    val empty = RefinableImage()
    if (layout == null) {
      return empty
    }
    val surface = sceneContext.surface ?: return empty
    val configuration = surface.configuration ?: return empty
    val facet = surface.model?.facet ?: return empty

    val resourceUrl = ResourceUrl.parse(layout) ?: return empty
    if (resourceUrl.type != ResourceType.LAYOUT) {
      return empty
    }
    val resourceResolver = configuration.resourceResolver
    val resourceValue = ApplicationManager.getApplication().runReadAction<String> {
      resourceResolver.resolve(resourceUrl, component.nlComponent.tagDeprecated)?.value
    } ?: return empty

    val file = File(resourceValue)
    if (!file.exists()) {
      return empty
    }
    val virtualFile = VfsUtil.findFileByIoFile(file, false) ?: return empty

    val psiFile = AndroidPsiUtils.getPsiFileSafely(surface.project, virtualFile) as? XmlFile ?: return empty
    val manager = ThumbnailManager.getInstance(facet)
    return manager.getThumbnail(psiFile, configuration, Dimension(rectangle.width.toInt(), rectangle.height.toInt()))
  }
}
