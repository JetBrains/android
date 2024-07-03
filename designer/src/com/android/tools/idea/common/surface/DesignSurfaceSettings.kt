/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.ZoomController
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundType
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Transient
import java.io.File

@Service(Service.Level.PROJECT)
@State(
  name = "DesignSurfaceV2",
  storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],
)
class DesignSurfaceSettings : PersistentStateComponent<SurfaceState> {

  var surfaceState: SurfaceState = SurfaceState()
    private set

  override fun getState(): SurfaceState = surfaceState

  override fun loadState(state: SurfaceState) {
    surfaceState = state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DesignSurfaceSettings =
      project.getService(DesignSurfaceSettings::class.java)
  }
}

@Suppress("MemberVisibilityCanBePrivate")
class SurfaceState {
  /**
   * The map of file path and zoom level. We use path string here because [PersistentStateComponent]
   * doesn't support [File] type. This field is public because [PersistentStateComponent] needs to
   * access its getter and setter. Do not access this field directly, use [saveFileScale] and
   * [loadFileScale] instead.
   */
  var filePathToZoomLevelMap: MutableMap<String, Double> = HashMap()

  /**
   * The map of file path and the drawable background type. We use path string here because
   * [PersistentStateComponent] doesn't support [File] type. This field is public because
   * [PersistentStateComponent] needs to access its getter and setter. Do not access this field
   * directly, use [saveDrawableBackgroundType] and [loadDrawableBackgroundType] instead.
   */
  var filePathToDrawableBackgroundType: MutableMap<String, DrawableBackgroundType> = HashMap()

  /**
   * Remember the last [DrawableBackgroundType] use selects. This [DrawableBackgroundType] is
   * applied when user opens a drawable file which it is never opened before.
   */
  var lastSelectedDrawableBackgroundType: DrawableBackgroundType = DrawableBackgroundType.NONE

  @Transient
  fun loadFileScale(project: Project, file: VirtualFile, zoomController: ZoomController?): Double? {
    val relativePath = getRelativePathInProject(project, file) ?: return null

    return filePathToZoomLevelMap[relativePath.appendStoreId(zoomController)]
  }

  @Transient
  fun saveFileScale(project: Project, file: VirtualFile, zoomController: ZoomController?) {
    val relativePath = getRelativePathInProject(project, file) ?: return
    val zoomLevelMapKey = relativePath.appendStoreId(zoomController)
    if (zoomController?.scale == null) {
      filePathToZoomLevelMap.remove(zoomLevelMapKey)
    } else {
      filePathToZoomLevelMap[zoomLevelMapKey] = zoomController.scale
    }
  }

  @Transient
  fun loadDrawableBackgroundType(project: Project, file: VirtualFile): DrawableBackgroundType {
    val relativePath =
      getRelativePathInProject(project, file) ?: return lastSelectedDrawableBackgroundType
    return filePathToDrawableBackgroundType[relativePath] ?: lastSelectedDrawableBackgroundType
  }

  @Transient
  fun saveDrawableBackgroundType(
    project: Project,
    file: VirtualFile,
    type: DrawableBackgroundType,
  ) {
    lastSelectedDrawableBackgroundType = type
    val relativePath = getRelativePathInProject(project, file) ?: return
    filePathToDrawableBackgroundType[relativePath] = type
  }
}

@Suppress("UnstableApiUsage")
private fun getRelativePathInProject(project: Project, file: VirtualFile): String? {
  val projectBasePath = project.basePath ?: return null
  val filePath = file.let { BackedVirtualFile.getOriginFileIfBacked(it) }.path
  return FileUtilRt.getRelativePath(projectBasePath, filePath, File.separatorChar, true)
}

/**
 * @param zoomController The [ZoomController] containing a store id (it can be null).
 * @return The key of the map where the scale is stored, if the store id is null returns only the
 *   path of the file.
 */
private fun String.appendStoreId(zoomController: ZoomController?): String {
  return zoomController?.storeId?.let { storeId -> "$this:$storeId" } ?: this
}
