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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.uibuilder.api.PaletteComponentHandler
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
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
import java.util.concurrent.CompletableFuture
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
  private val myDesignSurfaceSupplier: Supplier<DesignSurface<*>?>,
  private val myDependencyManager: DependencyManager
) {
  class ImageAndDimension(val image: BufferedImage, val dimension: Dimension, val rendering: Future<*>?, val disposal: Future<*>?)

  @VisibleForTesting
  var renderTimeoutMillis = 600L

  @AndroidCoordinate
  fun createPreview(component: JComponent, item: Palette.Item): ImageAndDimension {
    val size: Dimension
    var image: Image?
    val scaleContext = ScaleContext.create(component)
    val future = if (myDependencyManager.needsLibraryLoad(item)) null else renderDragImage(item)
    val (renderedItem, disposal) = try {
      future?.get(renderTimeoutMillis, TimeUnit.MILLISECONDS)
    }
    catch (_: Exception) {
      null
    } ?: Pair(null, null)

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
    return ImageAndDimension(bufferedImage, size, future, disposal)
  }

  @VisibleForTesting
  private fun renderDragImage(item: Palette.Item): CompletableFuture<Pair<BufferedImage?, Future<*>?>> {
    val scene = sceneView
    val xml = scene?.let { constructPreviewXml(it, item) } ?: return CompletableFuture.completedFuture(Pair(null, null))

    return getRenderTask(scene.sceneManager.model.configuration)
      .thenCompose { renderTask -> renderImage(renderTask, xml) }
      .thenApply { (renderTask, renderResult) ->
        val image = renderResult?.let { extractImage(it) }
        val disposal = renderTask?.dispose()
        Pair(image, disposal)
      }
  }

  private fun constructPreviewXml(scene: SceneView, item: Palette.Item): String? {
    val model = scene.sceneManager.model
    val elementFactory = XmlElementFactory.getInstance(model.project)
    val xml = item.dragPreviewXml
    if (xml == PaletteComponentHandler.NO_PREVIEW) {
      return null
    }
    val tag = try {
      elementFactory.createTagFromText(xml)
    } catch (exception: IncorrectOperationException) {
      return null
    }
    val component = runWriteAction {
      model.createComponent(tag, null, null, InsertType.CREATE_PREVIEW)
    } ?: return null

    // Some components require a parent to render correctly.
    val componentTag = component.tag ?: return null
    return LINEAR_LAYOUT.format(CONTAINER_ID, componentTag.text)
  }

  private fun getRenderTask(configuration: Configuration): CompletableFuture<RenderTask?> {
    val module = configuration.module ?: return CompletableFuture.completedFuture(null)
    val facet = AndroidFacet.getInstance(module) ?: return CompletableFuture.completedFuture(null)
    val renderService = RenderService.getInstance(module.project)
    val logger = renderService.createLogger(module)
    return renderService.taskBuilder(facet, configuration, logger).build()
  }

  private fun extractImage(result: RenderResult): BufferedImage? {
    val image = result.renderedImage
    if (!image.isValid) {
      return null
    }
    val view = result.rootViews.firstOrNull()?.children?.firstOrNull() ?: return null
    if (image.height < view.bottom || image.width < view.right || view.bottom <= view.top || view.right <= view.left) {
      return null
    }
    val scene = sceneView ?: return null
    @SwingCoordinate
    val shadowIncrement = 1 + Coordinates.getSwingDimension(scene, SHADOW_SIZE)
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
    get() = myDesignSurfaceSupplier.get()?.let {
      if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
        val sceneScale = it.focusedSceneView?.sceneManager?.sceneScalingFactor ?: 1.0f
        return it.scale * it.screenScalingFactor / sceneScale
      } else {
        return it.scale * it.screenScalingFactor
      }
    }

  private val sceneView: SceneView?
    get() = myDesignSurfaceSupplier.get()?.focusedSceneView

  private fun renderImage(renderTask: RenderTask?, xml: String): CompletableFuture<Pair<RenderTask?, RenderResult?>> {
    if (renderTask == null) {
      return CompletableFuture.completedFuture(Pair(null, null))
    }
    val file = runReadAction {
      PsiFileFactory
        .getInstance(renderTask.context.module.ideaModule.project)
        .createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml)
    }
    assert(file is XmlFile)
    renderTask.setXmlFile((file as XmlFile))
    renderTask.setTransparentBackground()
    renderTask.setDecorations(false)
    renderTask.setRenderingMode(SessionParams.RenderingMode.V_SCROLL)
    renderTask.context.folderType = ResourceFolderType.LAYOUT
    renderTask.inflate()
    return renderTask.render().thenApply { result -> Pair(renderTask, result) }
  }
}
