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
package com.android.tools.idea.uibuilder.palette

import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.uibuilder.api.PaletteComponentHandler
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.RasterFormatException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.JComponent
import kotlin.math.min

@AndroidCoordinate
private const val SHADOW_SIZE = 6
private const val PREVIEW_PLACEHOLDER_FILE = "preview.xml"
private const val CONTAINER_ID = "TopLevelContainer"
private const val LINEAR_LAYOUT = """<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/%1${"$"}s"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical">
  %2${"$"}s
</LinearLayout>
"""

/**
 * Creates a preview image that is used when dragging an item from the palette.
 * If possible a image is generated from the actual Android view. Otherwise we
 * simply generate the image from the icon used in the palette.
 */
class PreviewProvider(
  private val myDesignSurfaceSupplier: Supplier<DesignSurface?>,
  private val myDependencyManager: DependencyManager
) : Disposable {
  class ImageAndDimension(val image: BufferedImage, val dimension: Dimension)

  private var myRenderTask: RenderTask? = null

  @VisibleForTesting
  var renderTaskTimeoutMillis = 300L

  @VisibleForTesting
  var renderTimeoutMillis = 300L

  @AndroidCoordinate
  fun createPreview(component: JComponent, item: Palette.Item): ImageAndDimension {
    val size: Dimension
    var image: Image?
    val scaleContext = ScaleContext.create(component)
    val renderedItem: Image? = if (myDependencyManager.needsLibraryLoad(item)) null else renderDragImage(item)
    image = if (renderedItem == null) {
      val icon = item.icon
      IconLoader.toImage(icon, scaleContext)
    } else {
      ImageUtil.ensureHiDPI(renderedItem, scaleContext)
    }
    val width = ImageUtil.getRealWidth(image!!)
    val height = ImageUtil.getRealHeight(image)
    image = ImageUtil.scaleImage(image, currentScale ?: 1.0)
    size = Dimension(width, height)

    // Workaround for https://youtrack.jetbrains.com/issue/JRE-224
    val inUserScale = !SystemInfo.isWindows || !StartupUiUtil.isJreHiDPI(component)
    val bufferedImage = ImageUtil.toBufferedImage(image, inUserScale)
    return ImageAndDimension(bufferedImage, size)
  }

  @VisibleForTesting
  fun renderDragImage(item: Palette.Item): BufferedImage? {
    val sceneView = sceneView
    if (sceneView == null) {
      disposeRenderTaskNoWait()
      return null
    }
    val elementFactory = XmlElementFactory.getInstance(sceneView.model.project)
    var xml = item.dragPreviewXml
    if (xml == PaletteComponentHandler.NO_PREVIEW) {
      return null
    }
    val tag = try {
      elementFactory.createTagFromText(xml)
    } catch (exception: IncorrectOperationException) {
      return null
    }
    val model = sceneView.sceneManager.model
    val component = runWriteAction {
      model.createComponent(
        sceneView.surface, tag, null, null, InsertType.CREATE_PREVIEW
      )
    } ?: return null

    // Some components require a parent to render correctly.
    val componentTag = component.tag ?: return null
    xml = LINEAR_LAYOUT.format(CONTAINER_ID, componentTag.text)
    try {
      myRenderTask = getRenderTask(model.configuration).get(renderTaskTimeoutMillis, TimeUnit.MILLISECONDS)
    } catch (ex: Exception) {
      myRenderTask = null
      return null
    }
    val result = renderImage(renderTimeoutMillis, myRenderTask, xml)
    disposeRenderTaskNoWait()
    if (result == null) {
      return null
    }
    val image = result.renderedImage
    if (!image.isValid) {
      return null
    }
    val view = result.rootViews.firstOrNull()?.children?.firstOrNull() ?: return null
    if (image.height < view.bottom || image.width < view.right || view.bottom <= view.top || view.right <= view.left) {
      return null
    }
    @SwingCoordinate
    val shadowIncrement = 1 + Coordinates.getSwingDimension(sceneView, SHADOW_SIZE)
    val imageCopy = image.copy ?: return null
    return try {
      imageCopy.getSubimage(
        view.left,
        view.top,
        min(view.right + shadowIncrement, image.width),
        min(view.bottom + shadowIncrement, image.height)
      )
    } catch (ignore: RasterFormatException) {
      // catch exception
      null
    }
  }

  private val currentScale: Double?
    get() = myDesignSurfaceSupplier.get()?.let { it.scale * it.screenScalingFactor }

  private val sceneView: SceneView?
    get() = myDesignSurfaceSupplier.get()?.focusedSceneView

  private fun getRenderTask(configuration: Configuration): Future<RenderTask> {
    val module = configuration.module
    if (myRenderTask != null && myRenderTask!!.context.module === module) {
      return Futures.immediateFuture(myRenderTask)
    }
    disposeRenderTaskNoWait()
    if (module == null) {
      return Futures.immediateFuture(null)
    }
    val facet = AndroidFacet.getInstance(module) ?: return Futures.immediateFuture(null)
    val renderService = RenderService.getInstance(module.project)
    val logger = renderService.createLogger(facet)
    return renderService.taskBuilder(facet, configuration)
      .withLogger(logger)
      .build()
  }

  override fun dispose() {
    if (myRenderTask != null) {
      // Wait until async dispose finishes
      Futures.getUnchecked(myRenderTask!!.dispose())
      myRenderTask = null
    }
  }

  private fun disposeRenderTaskNoWait() {
    myRenderTask?.dispose()
    myRenderTask = null
  }

  private fun renderImage(renderTimeoutMillis: Long, renderTask: RenderTask?, xml: String): RenderResult? {
    if (renderTask == null) {
      return null
    }
    val file = PsiFileFactory
      .getInstance(renderTask.context.module.project)
      .createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml)
    assert(file is XmlFile)
    renderTask.setXmlFile((file as XmlFile))
    renderTask.setTransparentBackground()
    renderTask.setDecorations(false)
    renderTask.setRenderingMode(SessionParams.RenderingMode.V_SCROLL)
    renderTask.context.folderType = ResourceFolderType.LAYOUT
    renderTask.inflate()
    try {
      return renderTask.render()[renderTimeoutMillis, TimeUnit.MILLISECONDS]
    } catch (ex: Exception) {
      Logger.getInstance(PreviewProvider::class.java).debug(ex)
    }
    return null
  }
}
