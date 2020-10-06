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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import com.intellij.util.xmlb.annotations.Transient
import java.io.File

@State(name = "DesignSurface", storages = [Storage("designSurface.xml")])
class DesignSurfaceSettings : PersistentStateComponent<SurfaceState> {

  var surfaceState: SurfaceState = SurfaceState()
    private set

  override fun getState(): SurfaceState? = surfaceState

  override fun loadState(state: SurfaceState) {
    surfaceState = state
  }

  companion object {
    @JvmStatic
    fun getInstance(): DesignSurfaceSettings = ServiceManager.getService(DesignSurfaceSettings::class.java)
  }
}

class SurfaceState {
  /**
   * The map of file path and zoom level. We use path string here because [PersistentStateComponent] doesn't support [File] type.
   * This field is public because [PersistentStateComponent] needs to access its getter and setter. Do not access this field directly,
   * use [saveFileScale] and [loadFileScale] instead.
   */
  @field:Suppress("MemberVisibilityCanBePrivate")
  var filePathToZoomLevelMap: MutableMap<String, Double> = HashMap()

  @Transient
  fun loadFileScale(file: PsiFile): Double? {
    val relativePath = getRelativePathInProject(file) ?: return null
    return filePathToZoomLevelMap[relativePath]
  }

  @Transient
  fun saveFileScale(file: PsiFile, scale: Double?) {
    val relativePath = getRelativePathInProject(file) ?: return
    if (scale == null) {
      filePathToZoomLevelMap.remove(relativePath)
    }
    else {
      filePathToZoomLevelMap[relativePath] = scale
    }
  }
}

private fun getRelativePathInProject(file: PsiFile): String? {
  val basePath = file.project.basePath ?: return null
  val filePath = file.virtualFile?.path ?: return null
  return FileUtilRt.getRelativePath(basePath, filePath, File.separatorChar, true)
}
