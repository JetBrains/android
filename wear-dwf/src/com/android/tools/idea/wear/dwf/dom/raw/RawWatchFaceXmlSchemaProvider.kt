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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants.TAG_WATCH_FACE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.MergedManifestSnapshotComputeListener
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult
import com.android.tools.idea.wear.dwf.analytics.DeclarativeWatchFaceUsageTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.FileContentUtilCore
import com.intellij.xml.XmlSchemaProvider
import com.intellij.xml.util.XmlUtil
import kotlin.time.Duration
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile
import org.jetbrains.annotations.NonNls

/**
 * Provides XSD Schemas based on the current WFF version defined in the merged manifest.
 *
 * If the WFF version in the merged manifest is not valid, a fallback version is used instead.
 *
 * Because the schema depends on the merged manifest, and that the merged manifest can be updated
 * asynchronously, we rely on [RawWatchFaceXmlSchemaUpdater] to reparse declarative watch face files
 * to ensure they're using the WFF version defined in the merged manifest.
 *
 * @see CurrentWFFVersionService
 * @see RawWatchFaceXmlSchemaUpdater
 */
class RawWatchfaceXmlSchemaProvider() : XmlSchemaProvider() {

  override fun getSchema(url: @NonNls String, module: Module?, baseFile: PsiFile): XmlFile? {
    if (module == null) return null
    val (schemaVersion, isFallback) =
      CurrentWFFVersionService.getInstance().getCurrentWFFVersion(module) ?: return null

    DeclarativeWatchFaceUsageTracker.getInstance().trackXmlSchemaUsed(schemaVersion, isFallback)

    return XmlUtil.findXmlFile(
      baseFile,
      VfsUtilCore.urlToPath(
        VfsUtilCore.toIdeaUrl(FileUtil.unquote(schemaVersion.schemaUrl.toExternalForm()), false)
      ),
    )
  }

  override fun isAvailable(file: XmlFile) =
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get() &&
      isDeclarativeWatchFaceFile(file)
}

/** [StartupActivity] that activates [RawWatchFaceXmlSchemaUpdater] */
private class RawWatchFaceXmlSchemaUpdaterStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<RawWatchFaceXmlSchemaUpdater>()
  }
}

/**
 * This class attempts to fix a couple of issues regarding the use of XSD files based on a property
 * set within a manifest file.
 *
 * The first problem is that we rely on the merged manifest to get the WFF version. The merged
 * manifest is not available immediately, and we cannot wait for its computation to end in
 * [RawWatchfaceXmlSchemaProvider.getSchema] as that's called on the EDT. We could read the manifest
 * files directly and determine the WFF, but we'd be potentially introducing extra issues that the
 * merged manifest already takes care of.
 *
 * The second problem is that the schema returned by [RawWatchfaceXmlSchemaProvider] is cached and
 * the cache dependencies do not include [com.intellij.psi.util.PsiModificationTracker] or any
 * changes to the merged manifest, meaning that any changes to the WFF version in the merged
 * manifest will go unnoticed. Furthermore, if the user opens Studio with a declarative watch face
 * file as the first file to be open before the merged manifest is computed, we can end up without
 * any WFF schema being used. Once the merged manifest has finished computing, the editor will not
 * update itself.
 *
 * This class attempts to fix both problems by forcing the declarative watch face files to be
 * reparsed whenever we detect that a new successful merged manifest snapshot has been computed.
 */
@Service(Service.Level.PROJECT)
private class RawWatchFaceXmlSchemaUpdater private constructor(val project: Project) :
  MergedManifestSnapshotComputeListener, Disposable {

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    Disposer.register(this, connection)
    connection.subscribe(MergedManifestSnapshotComputeListener.TOPIC, this)
  }

  override fun snapshotCreationEnded(token: Any, duration: Duration, result: MergeResult) {
    if (result != MergeResult.SUCCESS) return

    val declarativeWatchFaceFiles = project.getDeclarativeWatchFaceFiles()
    if (declarativeWatchFaceFiles.isEmpty()) return

    ApplicationManager.getApplication().invokeLaterOnWriteThread {
      // reparse the files for the caches to be dropped and the schemas recomputed with
      // the latest merged manifest
      FileContentUtilCore.reparseFiles(declarativeWatchFaceFiles)
    }
  }

  override fun snapshotCreationStarted(token: Any) {}

  override fun dispose() {}

  private fun Project.getDeclarativeWatchFaceFiles() =
    getAndroidFacets()
      .flatMap { facet ->
        StudioResourceRepositoryManager.getProjectResources(facet).getResources(
          ResourceNamespace.RES_AUTO,
          ResourceType.RAW,
        ) {
          val xmlFile = it.getSourceAsVirtualFile()?.findPsiFile(project) as? XmlFile
          xmlFile?.rootTag?.name == TAG_WATCH_FACE
        }
      }
      .mapNotNull { it.getSourceAsVirtualFile() }
}
