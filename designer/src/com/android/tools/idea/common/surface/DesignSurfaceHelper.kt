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
@file:JvmName("DesignSurfaceHelper")
package com.android.tools.idea.common.surface

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.Bridge
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.npw.assetstudio.IconGenerator
import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodePreview
import com.google.common.io.CharStreams
import com.intellij.ide.DataManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import java.awt.Dimension
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.io.IOException
import java.io.InputStreamReader
import javax.swing.JComponent

private val logger: Logger by lazy { Logger.getInstance("DesignSurfaceHelper") }

fun moduleContainsResource(facet: AndroidFacet, type: ResourceType, name: String): Boolean {
  return StudioResourceRepositoryManager.getModuleResources(facet).hasResources(ResourceNamespace.TODO(), type, name)
}

fun copyVectorAssetToMainModuleSourceSet(project: Project, facet: AndroidFacet, asset: String) {
  val path = MaterialDesignIcons.getPathForBasename(asset) ?: run {
    logger.warn("Cannot find the material icon path for $asset")
    return@copyVectorAssetToMainModuleSourceSet
  }

  try {
    val inputStream = IconGenerator::class.java.classLoader.getResourceAsStream(path) ?: run {
      logger.warn("Cannot load the material icon for $asset")
      return@copyVectorAssetToMainModuleSourceSet
    }
    InputStreamReader(inputStream, Charsets.UTF_8).use {
      reader -> createResourceFile(project, facet, FD_RES_DRAWABLE, asset + DOT_XML, CharStreams.toString(reader))
    }
  }
  catch (exception: IOException) {
    logger.warn(exception)
  }
}

fun copyLayoutToMainModuleSourceSet(project: Project, facet: AndroidFacet, layout: String, @Language("XML") xml: String) {
  val message = "Do you want to copy layout $layout to your main module source set?"

  if (Messages.showYesNoDialog(project, message, "Copy Layout", Messages.getQuestionIcon()) == Messages.NO) {
    return
  }

  createResourceFile(project, facet, FD_RES_LAYOUT, layout + DOT_XML, xml)
}

private fun createResourceFile(project: Project,
                               facet: AndroidFacet,
                               resourceDirectory: String,
                               resourceFileName: String,
                               resourceFileContent: CharSequence) {
  WriteCommandAction.runWriteCommandAction(project) {
    try {
      val directory = getResourceDirectoryChild(project, facet, resourceDirectory) ?: return@runWriteCommandAction

      val document = FileDocumentManager.getInstance().getDocument(directory.createChildData(project, resourceFileName))!!

      if (document is DocumentImpl && SystemInfo.isWindows) {
        document.setAcceptSlashR(true)
      }
      document.setText(resourceFileContent)
    }
    catch (exception: IOException) {
      logger.warn(exception)
    }
  }
}

@Throws(IOException::class)
private fun getResourceDirectoryChild(project: Project, facet: AndroidFacet, child: String): VirtualFile? {
  val resourceDirectory = ResourceFolderManager.getInstance(facet).primaryFolder

  if (resourceDirectory == null) {
    logger.warn("resourceDirectory is null")
    return null
  }

  return resourceDirectory.findChild(child) ?: return resourceDirectory.createChildDirectory(project, child)
}

/**
 * If a native crash caused by layoutlib is detected, show an error message instead of the design surface in the workbench.
 * This includes a hyperlink that will re-enable the design surface and run the {@link Runnable} argument.
 */
fun WorkBench<DesignSurface<*>>.handleLayoutlibNativeCrash(runnable: Runnable) {
  val message = "The preview has been disabled following a crash in the rendering engine. If the problem persists, please report the issue."
  val actionData = ActionData("Re-enable rendering") {
    Bridge.setNativeCrash(false)
    showLoading("Loading...")
    runnable.run()
  }
  loadingStopped(message, actionData)
}

/**
 * Create an [AWTEventListener] which checks the mouse position to determine if the [zoomControlComponent] should be shown.
 */
fun createZoomControlAutoHiddenListener(zoomControlPaneOwner: JComponent, zoomControlComponent: JComponent): AWTEventListener {
  return AWTEventListener { event ->
    val id: Int = event.id
    if (id == MouseEvent.MOUSE_ENTERED) {
      if (!zoomControlPaneOwner.isShowing) {
        zoomControlComponent.isVisible = false
        return@AWTEventListener
      }
      val mouseEvent = event as MouseEvent
      val location = mouseEvent.locationOnScreen
      val screenLocation = zoomControlPaneOwner.locationOnScreen
      val rect = zoomControlPaneOwner.visibleRect
      rect.translate(screenLocation.x, screenLocation.y)
      zoomControlComponent.isVisible = rect.contains(location)
    }
  }
}

/**
 * Find the scale value which can display all the [contentSize] on the screen.
 * This function tries to fit both width and height of [contentSize] into the current scroll pane of [DesignSurface].
 */
@SurfaceScale
fun DesignSurface<*>.getFitContentIntoWindowScale(contentSize: Dimension): Double {
  val availableWidth = extentSize.width
  val availableHeight = extentSize.height

  @SurfaceScale val scaleX: Double = if (size.width == 0) 1.0 else availableWidth.toDouble() / contentSize.width
  @SurfaceScale val scaleY: Double = if (size.height == 0) 1.0 else availableHeight.toDouble() / contentSize.height
  return minOf(scaleX, scaleY, myMaxFitIntoScale)
}

/**
 * Helper function to set the visibilities of all [SceneView]s in the [DesignSurface].
 */
fun DesignSurface<*>.updateSceneViewVisibilities(visibilityFunc: (SceneView) -> Boolean) {
  val sceneViews = sceneManagers.flatMap { it.sceneViews }
  sceneViews.forEach { view -> view.isVisible = visibilityFunc(view) }
  revalidateScrollArea()
}

/**
 * Obtain the [DesignSurface] associated to a [FileEditor] if any.
 */
fun FileEditor.getDesignSurface(): DesignSurface<*>? =
  when (this) {
    is TextEditorWithPreview -> previewEditor.getDesignSurface()
    // Check if there is a design surface in the context of presentation. For example, Compose and CustomView preview.
    is SourceCodePreview -> currentRepresentation?.component?.let {
      DataManager.getInstance().getDataContext(it).getData(DESIGN_SURFACE)
    }
    else -> DataManager.getInstance().getDataContext(component).getData(DESIGN_SURFACE)
  }