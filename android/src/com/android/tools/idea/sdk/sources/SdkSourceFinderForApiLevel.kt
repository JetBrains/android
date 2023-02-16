/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.sdk.sources

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider.Companion.REQUIRED_SOURCES_KEY
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.SdkInstallListener
import com.intellij.debugger.SourcePosition
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import java.util.Locale
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

private val missingSourcesFileContentsFormat = """
  /*********************************************************************
   * The Android SDK of the device under debug has API level %d.
   * Android SDK source code for this API level cannot be found.
   ********************************************************************

""".trimIndent()

private const val MISSING_SOURCES_FILE_NAME = "android-%d/UnavailableSource"

/**
 * Finds a [SourcePosition] for a specific API level
 *
 * If the sources are not downloaded, creates a stub file and displays a banner offering user to download the sources.
 */
internal class SdkSourceFinderForApiLevel(val project: Project, private val apiLevel: Int) {
  private val missingSourcesFile: PsiFile by lazy(SYNCHRONIZED) { createMissingSourcesFile() }

  @UiThread
  fun getSourcePosition(file: PsiFile, lineNumber: Int): SourcePosition {
    return getSourceFileForApiLevel(file, lineNumber) ?: getPositionForMissingSources()
  }

  private fun getSourceFileForApiLevel(file: PsiFile, lineNumber: Int): SourcePosition? {
    val relPath = getRelPathForJavaSource(file)
    if (relPath == null) {
      thisLogger().debug("getApiSpecificPsi returned null because relPath is null for file: " + file.name)
      return null
    }

    val sourceFolder = createSourcePackageForApiLevel() ?: return null
    val virtualFile = sourceFolder.findFileByRelativePath(relPath)
    if (virtualFile == null) {
      thisLogger().debug("getSourceForApiLevel returned null because $relPath is not present in $sourceFolder")
      return null
    }

    val apiSpecificSourceFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null

    return SourcePosition.createFromLine(apiSpecificSourceFile, lineNumber)
  }

  private fun getRelPathForJavaSource(file: PsiFile): String? {
    return when (file.fileType) {
      JavaFileType.INSTANCE -> {
        // When the compilation SDK sources are present, they are indexed and the incoming PsiFile is a JavaFileType that refers to them.
        // The relative path for the same file in target SDK sources can be directly determined.
        val sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(file.virtualFile)
        if (sourceRoot == null) {
          thisLogger().debug("Could not determine source root for file: " + file.virtualFile.path)
          null
        }
        else {
          VfsUtilCore.getRelativePath(file.virtualFile, sourceRoot)
        }
      }

      JavaClassFileType.INSTANCE -> {
        // When the compilation SDK sources are not present, the incoming PsiFile is a JavaClassFileType coming from the compilation SDK
        // android.jar. We can figure out the relative path to the class file, and make the assumption that the java file will have the
        // same path.
        val virtualFile = file.virtualFile
        val relativeClassPath = VfsUtilCore.getRelativePath(virtualFile, VfsUtilCore.getRootFile(virtualFile))

        // The class file should end in ".class", but we're interested in the corresponding java file.
        relativeClassPath?.changeClassExtensionToJava()
      }

      else -> null
    }
  }

  private fun createSourcePackageForApiLevel(): VirtualFile? {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(StudioLoggerProgressIndicator(this::class.java))

    for (sourcePackage in sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_ANDROID_SOURCES)) {
      val typeDetails = sourcePackage.typeDetails
      if (typeDetails !is DetailsTypes.ApiDetailsType) {
        thisLogger().warn("Unable to get type details for source package @ " + sourcePackage.location)
        continue
      }
      if (apiLevel == typeDetails.androidVersion.apiLevel) {
        val sourceFolder = VfsUtil.findFile(sourcePackage.location, true)
        if (sourceFolder?.isValid == true) {
          return sourceFolder
        }
      }
    }

    return null
  }

  private fun getPositionForMissingSources(): SourcePosition {
    return SourcePosition.createFromLine(missingSourcesFile, -1)
  }


  private fun createMissingSourcesFile(): PsiFile {
    val content = String.format(Locale.getDefault(), missingSourcesFileContentsFormat, apiLevel)
    val name = MISSING_SOURCES_FILE_NAME.format(apiLevel)
    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(name, JavaLanguage.INSTANCE, content, true, true)
    val file = psiFile.virtualFile

    // Technically, VirtualFile.setWritable() can throw, but we will have a LightVirtualFile which doesn't throw.
    runCatching { file.isWritable = false }

    val messageBus = project.messageBus
    messageBus.connect(messageBus).subscribe(SdkInstallListener.TOPIC, SdkInstallListener { installed, _ ->
      val path = DetailsTypes.getSourcesPath(AndroidVersion(apiLevel))
      if (installed.find { it.path == path } != null) {
        if (file.isValid) {
          FileEditorManager.getInstance(project).closeFile(file)
        }
      }
    })

    // Add data indicating that we want to put up a banner offering to download sources.
    file.putUserData(REQUIRED_SOURCES_KEY, apiLevel)
    return psiFile
  }
}

fun String.changeClassExtensionToJava() =
  if (endsWith(SdkConstants.DOT_CLASS)) substring(0, length - SdkConstants.DOT_CLASS.length) + SdkConstants.DOT_JAVA else this

