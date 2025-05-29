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

import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.EXT_ANDROID_PACKAGE
import com.android.SdkConstants.EXT_APP_BUNDLE
import com.android.SdkConstants.FD_RES
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.ReformatUtil
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.MISSING_MAIN_MODULE
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.UNKNOWN
import com.android.tools.idea.wear.dwf.importer.wfs.WFSImportResult.Error.Type.UNSUPPORTED_FILE_EXTENSION
import com.android.tools.idea.wear.dwf.importer.wfs.extractors.AndroidAppBundleExtractor
import com.android.tools.idea.wear.dwf.importer.wfs.extractors.ApkExtractor
import com.android.tools.idea.wear.dwf.importer.wfs.extractors.ExtractedItem
import com.android.utils.FileUtils
import com.android.utils.StdLogger
import com.android.utils.XmlUtils
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeBytes
import com.intellij.util.io.URLUtil
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidFileTemplateProvider
import org.jetbrains.android.dom.resources.Resources
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.annotations.TestOnly
import org.w3c.dom.Document

private val LOG = Logger.getInstance(WatchFaceStudioFileImporter::class.java)

/**
 * Imports watch faces compiled by
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html) into an
 * already existing and open project. These compiled files are `.aab` and `.apk` files and can be
 * built either by publishing a watch face, or deploying a watch face to device, from Watch Face
 * Studio.
 *
 * Existing files may be overwritten if they have the same path as the files that are imported.
 */
@Service(Service.Level.PROJECT)
class WatchFaceStudioFileImporter
private constructor(
  private val project: Project,
  private val defaultDispatcher: CoroutineDispatcher,
  private val ioDispatcher: CoroutineDispatcher,
) {

  private val extractorByFileExtension =
    mapOf(
      EXT_APP_BUNDLE to AndroidAppBundleExtractor(ioDispatcher),
      EXT_ANDROID_PACKAGE to ApkExtractor(ioDispatcher),
    )

  val supportedFileTypes = extractorByFileExtension.keys

  private constructor(
    project: Project
  ) : this(
    project = project,
    defaultDispatcher = Dispatchers.Default,
    ioDispatcher = Dispatchers.IO,
  )

  suspend fun import(filePathToImport: Path): WFSImportResult =
    withContext(defaultDispatcher) {
      val mainAndroidFacet =
        project.modules
          .mapNotNull { it.androidFacet }
          .firstOrNull { it.getModuleSystem().isProductionAndroidModule() }
          ?: return@withContext WFSImportResult.Error(MISSING_MAIN_MODULE)

      val fileExtractor =
        extractorByFileExtension[filePathToImport.extension]
          ?: return@withContext WFSImportResult.Error(UNSUPPORTED_FILE_EXTENSION)

      try {
        withContext(ioDispatcher) {
          fileExtractor.extract(filePathToImport).collect { item ->
            when (item) {
              is ExtractedItem.Manifest -> importManifest(mainAndroidFacet.mainManifestPath(), item)
              is ExtractedItem.Resource ->
                importResource(mainAndroidFacet.resolveResourcePath(item.filePath), item)
            }
          }
        }
        WFSImportResult.Success
      } catch (e: Throwable) {
        LOG.warn("An error occurred when importing the Watch Face Studio file.", e)
        WFSImportResult.Error()
      }
    }

  private suspend fun importManifest(
    manifestPath: Path,
    extractedManifest: ExtractedItem.Manifest,
  ) {
    val archiveManifestDocument = XmlUtils.parseDocument(extractedManifest.content, true)
    // The package can be different from the current app/module, resulting in an error when
    // deploying the watch face, unless we remove it.
    archiveManifestDocument.documentElement.removeAttribute(ATTR_PACKAGE)

    val manifestContent =
      if (manifestPath.exists()) {
        mergeManifests(manifestPath.toFile(), archiveManifestDocument)
      } else {
        XmlUtils.toXml(archiveManifestDocument)
      }

    writeAndReformat(manifestPath.findOrCreateVirtualFile(), manifestContent)
  }

  private fun mergeManifests(manifestFile: File, archiveManifest: Document): String {
    val archiveManifestFile = File.createTempFile("archiveManifest", ".xml") // will not be created
    val mergeReport =
      ManifestMerger2.newMerger(
          manifestFile,
          StdLogger(StdLogger.Level.WARNING),
          ManifestMerger2.MergeType.APPLICATION,
        )
        .withFeatures(
          ManifestMerger2.Invoker.Feature.SKIP_BLAME,
          ManifestMerger2.Invoker.Feature.NO_IMPLICIT_PERMISSION_ADDITION,
          ManifestMerger2.Invoker.Feature.HANDLE_VALUE_CONFLICTS_AUTOMATICALLY,
          ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT,
          ManifestMerger2.Invoker.Feature.KEEP_GOING_AFTER_ERRORS,
        )
        .asType(XmlDocument.Type.OVERLAY)
        .addFlavorAndBuildTypeManifest(archiveManifestFile)
        .withFileStreamProvider(
          object : ManifestMerger2.FileStreamProvider() {
            override fun getInputStream(file: File): InputStream? {
              if (FileUtils.isSameFile(file, archiveManifestFile)) {
                return XmlUtils.toXml(archiveManifest).byteInputStream()
              }
              return super.getInputStream(file)
            }
          }
        )
        .merge()

    return mergeReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)?.takeIf {
      mergeReport.result.isSuccess
    } ?: error("Failed to merge the manifest")
  }

  private suspend fun importResource(destinationPath: Path, resource: ExtractedItem.Resource) {
    when (resource) {
      is ExtractedItem.StringResource -> importStringResource(destinationPath, resource)
      is ExtractedItem.BinaryResource -> importBinaryResource(destinationPath, resource)
      is ExtractedItem.TextResource -> importTextResource(destinationPath, resource)
    }
  }

  private suspend fun importStringResource(
    destinationPath: Path,
    stringResource: ExtractedItem.StringResource,
  ) {
    val stringResourceFile =
      VfsUtil.findFile(destinationPath, true)
        ?: VfsUtil.createDirectories(destinationPath.parent.pathString).let { parentDirectory ->
          val fileName = destinationPath.fileName.pathString
          edtWriteAction {
            AndroidFileTemplateProvider.createFromTemplate(
                project,
                parentDirectory,
                AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE,
                fileName,
              )
              ?.containingFile
              ?.virtualFile ?: error("Expected $fileName to be created")
          }
        }

    val stringDomElement =
      AndroidUtils.loadDomElement(project, stringResourceFile, Resources::class.java)
        ?: error("Failed to load DomElement for $stringResourceFile")

    writeCommandAction(project, "Extracting String Resource") {
      val stringElement =
        stringDomElement.strings.find { it.name.stringValue == stringResource.name }
          ?: stringDomElement.addString().also { it.name.stringValue = stringResource.name }
      stringElement.stringValue = stringResource.value
    }
  }

  private suspend fun importBinaryResource(
    destinationPath: Path,
    resource: ExtractedItem.BinaryResource,
  ) {
    val destinationFile = destinationPath.findOrCreateVirtualFile()
    edtWriteAction { destinationFile.writeBytes(resource.binaryContent) }
  }

  private suspend fun importTextResource(
    destinationPath: Path,
    resource: ExtractedItem.TextResource,
  ) {
    val destinationFile = destinationPath.findOrCreateVirtualFile()
    writeAndReformat(destinationFile, resource.text)
  }

  private suspend fun writeAndReformat(destinationFile: VirtualFile, content: String) {
    val normalized = StringUtil.convertLineSeparators(content)
    readAndWriteAction {
      val document =
        FileDocumentManager.getInstance().getDocument(destinationFile)
          ?: error("Expected file document to exist")
      writeCommandAction(project, "Writing and Reformatting File") {
        document.setText(normalized)
        ReformatUtil.reformatAndRearrange(project, destinationFile, keepDocumentLocked = true)
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }
  }

  private fun AndroidFacet.mainManifestPath() =
    SourceProviders.getInstance(this)
      .mainIdeaSourceProvider
      ?.manifestFileUrls
      ?.singleOrNull()
      ?.urlToPath() ?: error("Expected a single manifest URL to exist")

  /**
   * Resolves the path of an extracted resource (which is in the form `res/<type>/<filename.xml>`)
   * to a resource path within the given [AndroidFacet]. The `res` from the [resourcePath] will be
   * replaced with the name of the res folder used by the facet.
   */
  private fun AndroidFacet.resolveResourcePath(resourcePath: Path): Path {
    val resDirectoryPath =
      SourceProviders.getInstance(this)
        .mainIdeaSourceProvider
        ?.resDirectoryUrls
        ?.singleOrNull()
        ?.urlToPath() ?: error("Expected a single res directory URL to exist")

    // remove the `res/` directory from the resource path to use the one from the facet instead
    return resDirectoryPath.resolve(resourcePath.withoutResDirectory())
  }

  private fun String.urlToPath() = URLUtil.extractPath(this).toNioPathOrNull()

  /** Removes the first `res` directory from the path, if any. */
  private fun Path.withoutResDirectory() =
    indexOfFirst { it.pathString == FD_RES }
      .takeIf { it >= 0 && it < count() }
      ?.let { subpath(it + 1, count()) } ?: this

  private suspend fun Path.findOrCreateVirtualFile(): VirtualFile {
    val parentDirectory =
      VfsUtil.createDirectories(parent.pathString)
        ?: error("expected ${parent.pathString} to be created")
    return edtWriteAction { parentDirectory.findOrCreateFile(fileName.pathString) }
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
      UNSUPPORTED_FILE_EXTENSION,
    }
  }
}

sealed class WFSImportException(message: String) : Exception(message) {
  class InvalidHoneyFaceFileException(message: String) : WFSImportException(message)
}
