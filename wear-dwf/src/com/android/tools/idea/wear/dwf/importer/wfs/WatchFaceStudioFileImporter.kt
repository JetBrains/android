/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs

import com.android.SdkConstants.FD_MAIN
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FD_SOURCES
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.MISSING_MAIN_MODULE
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.UNKNOWN
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Success
import com.android.tools.idea.wear.dwf.importer.wfs.extractors.WFSFileExtractor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlin.io.path.exists
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.annotations.TestOnly

private val LOG = Logger.getInstance(WatchFaceStudioFileImporter::class.java)

/**
 * Imports [WatchFaceStudio](https://developer.samsung.com/watch-face-studio/overview.html) files
 * (`.wfs`) to an already existing and open project.
 *
 * Existing files may be overwritten if they have the same path as the files that are imported.
 */
@Service(Service.Level.PROJECT)
class WatchFaceStudioFileImporter
private constructor(
  private val project: Project,
  private val defaultDispatcher: CoroutineDispatcher,
  private val ioDispatcher: CoroutineDispatcher,
  private val wfsFileExtractor: WFSFileExtractor = WFSFileExtractor(ioDispatcher = ioDispatcher),
) {

  private constructor(
    project: Project
  ) : this(
    project = project,
    defaultDispatcher = Dispatchers.Default,
    ioDispatcher = Dispatchers.IO,
  )

  suspend fun import(wfsFile: VirtualFile): WFSImportResult =
    withContext(defaultDispatcher) {
      val mainModuleRoot =
        AndroidRootUtil.findModuleRootFolderPath(
          project.modules.first { it.getModuleSystem().isProductionAndroidModule() }
        )
      if (mainModuleRoot == null) {
        return@withContext Error(MISSING_MAIN_MODULE)
      }
      val mainFolderPath = mainModuleRoot.toPath().resolve(FD_SOURCES).resolve(FD_MAIN)
      if (!mainFolderPath.exists()) {
        withContext(ioDispatcher) { mainFolderPath.toFile().mkdirs() }
      }
      val resFolderPath = mainFolderPath.resolve(FD_RES)
      if (!resFolderPath.exists()) {
        withContext(ioDispatcher) { resFolderPath.toFile().mkdirs() }
      }

      try {
        wfsFileExtractor.extract(wfsFile, mainFolderPath, resFolderPath)
      } catch (e: Throwable) {
        LOG.warn("An error occurred when importing the Watch Face Studio file.", e)
        return@withContext Error()
      } finally {
        edtWriteAction {
          LocalFileSystem.getInstance().findFileByNioFile(mainFolderPath)?.refresh(false, true)
        }
      }
      Success
    }

  companion object {
    fun getInstance(project: Project): WatchFaceStudioFileImporter = project.service()

    @TestOnly
    internal fun getInstanceForTest(
      project: Project,
      defaultDispatcher: CoroutineDispatcher,
      ioDispatcher: CoroutineDispatcher,
    ) =
      WatchFaceStudioFileImporter(
        project = project,
        defaultDispatcher = defaultDispatcher,
        ioDispatcher = ioDispatcher,
      )
  }
}

sealed class WFSImportResult {
  object Success : WFSImportResult()

  data class Error(val error: Type = UNKNOWN) : WFSImportResult() {
    enum class Type {
      UNKNOWN,
      MISSING_MAIN_MODULE,
    }
  }
}

sealed class WFSImportException(message: String) : Exception(message) {
  class InvalidHoneyFaceFileException(message: String) : WFSImportException(message)
}
