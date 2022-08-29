/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.android.SdkConstants
import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.sdk.AndroidSdks
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.intellij.lang.annotations.Language
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.function.Supplier

/**
 * AndroidPositionManager provides android java specific position manager augmentations on top of
 * [PositionManagerImpl] such as:
 *
 *  * Providing synthesized classes during android build.
 *  * Locating SDK sources that match the user's current target device.
 *
 * Unlike [PositionManagerImpl], [AndroidPositionManager] is not a cover-all position
 * manager and should fallback to other position managers if it encounters a situation it cannot
 * handle.
 */
class AndroidPositionManager(private val myDebugProcess: DebugProcessImpl) : PositionManagerImpl(
  myDebugProcess
) {
  private val myAndroidVersion: AndroidVersion?
  private var mySourceFolder: Supplier<VirtualFile?>? = null
  private var myGeneratedPsiFile: Supplier<PsiFile?>? = null
  private var debugSessionListenerRegistered = false

  init {
    myAndroidVersion = getAndroidVersionFromDebugSession(myDebugProcess.project)
    if (myAndroidVersion != null) {
      mySourceFolder = Suppliers.memoize { createSourcePackageForApiLevel() }
      myGeneratedPsiFile = Suppliers.memoize { createGeneratedPsiFile() }
    } else {
      mySourceFolder = Suppliers.ofInstance(null)
      myGeneratedPsiFile = Suppliers.ofInstance(null)
    }
  }

  @Throws(NoDataException::class)
  override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
    // For desugaring, we also need to add the extra synthesized classes that may contain the source position.
    val referenceTypes = DesugarUtils.addExtraClassesIfNeeded(myDebugProcess, position, super.getAllClasses(position), this)
    if (referenceTypes.isEmpty()) {
      throw NoDataException.INSTANCE
    }
    return referenceTypes
  }

  @Throws(NoDataException::class)
  override fun createPrepareRequests(
    requestor: ClassPrepareRequestor,
    position: SourcePosition
  ): List<ClassPrepareRequest> {
    // For desugaring, we also need to add prepare requests for the extra synthesized classes that may contain the source position.
    val requests =
      DesugarUtils.addExtraPrepareRequestsIfNeeded(myDebugProcess, requestor, position, super.createPrepareRequests(requestor, position))
    if (requests.isEmpty()) {
      throw NoDataException.INSTANCE
    }
    return requests
  }

  override fun getAcceptedFileTypes(): Set<FileType>? {
    // When setting breakpoints or debugging into SDK source, the Location's sourceName() method
    // returns a string of the form "FileName.java"; this resolves into a JavaFileType.
    return ImmutableSet.of(JavaFileType.INSTANCE)
  }

  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    if (location == null) {
      throw NoDataException.INSTANCE
    }
    if (myAndroidVersion == null) {
      LOG.debug("getSourcePosition cannot determine version from device.")
      throw NoDataException.INSTANCE
    }
    val project = myDebugProcess.project
    val file = getPsiFileByLocation(project, location)
    if (file == null || !AndroidSdks.getInstance().isInAndroidSdk(file)) {
      throw NoDataException.INSTANCE
    }

    // Since we have an Android SDK file, return the SDK source if it's available.
    val sourcePosition = getSourceFileForApiLevel(project, file, location)
    return sourcePosition ?: getGeneratedFileSourcePosition(project)

    // Otherwise, return a generated file with a comment indicating that sources are unavailable.
  }

  @VisibleForTesting
  override fun getPsiFileByLocation(project: Project, location: Location): PsiFile? {
    return super.getPsiFileByLocation(project, location)
  }

  private fun getSourceFileForApiLevel(project: Project, file: PsiFile, location: Location): SourcePosition? {
    val relPath = getRelPathForJavaSource(project, file)
    if (relPath == null) {
      LOG.debug("getApiSpecificPsi returned null because relPath is null for file: " + file.name)
      return null
    }
    val sourceFolder = mySourceFolder!!.get() ?: return null
    val vfile = sourceFolder.findFileByRelativePath(relPath)
    if (vfile == null) {
      LOG.debug("getSourceForApiLevel returned null because $relPath is not present in $sourceFolder")
      return null
    }
    val apiSpecificSourceFile = PsiManager.getInstance(project).findFile(vfile) ?: return null
    val lineNumber = DebuggerUtilsEx.getLineNumber(location, true)
    return SourcePosition.createFromLine(apiSpecificSourceFile, lineNumber)
  }

  private fun getGeneratedFileSourcePosition(project: Project): SourcePosition {
    val generatedPsiFile = myGeneratedPsiFile!!.get()

    // If we don't already have one, create a new listener that will close the generated files after the debugging session completes.
    // Since this method is always called on DebuggerManagerThreadImpl, there's no concern around locking here.
    if (!debugSessionListenerRegistered) {
      debugSessionListenerRegistered = true
      myDebugProcess.session.xDebugSession
        .addSessionListener(MyXDebugSessionListener(generatedPsiFile!!.virtualFile, project))
    }
    return SourcePosition.createFromLine(generatedPsiFile!!, -1)
  }

  private fun createGeneratedPsiFile(): PsiFile {
    val project = myDebugProcess.project
    val apiLevel = myAndroidVersion!!.apiLevel
    val fileContent = String.format(Locale.getDefault(), GENERATED_FILE_CONTENTS_FORMAT, apiLevel)
    val generatedPsiFile =
      PsiFileFactory.getInstance(project).createFileFromText(GENERATED_FILE_NAME, JavaLanguage.INSTANCE, fileContent, true, true)
    val generatedVirtualFile = generatedPsiFile.virtualFile
    try {
      generatedVirtualFile.isWritable = false
    } catch (e: IOException) {
      // Swallow. This isn't expected; but if it happens and the user can for some reason edit this file, it won't make any difference.
      LOG.info("Unable to set generated file not writable.", e)
    }

    // Add data indicating that we want to put up a banner offering to download sources.
    val requiredSourceDownload: List<AndroidVersion?> = ImmutableList.of(myAndroidVersion)
    generatedVirtualFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, requiredSourceDownload)
    return generatedPsiFile
  }

  private fun createSourcePackageForApiLevel(): VirtualFile? {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(StudioLoggerProgressIndicator(AndroidPositionManager::class.java))
    val sourcePackages = sdkManager.packages.getLocalPackagesForPrefix(SdkConstants.FD_ANDROID_SOURCES)
    for (sourcePackage in sourcePackages) {
      val typeDetails = sourcePackage.typeDetails
      if (typeDetails !is DetailsTypes.ApiDetailsType) {
        LOG.warn("Unable to get type details for source package @ " + sourcePackage.location)
        continue
      }
      val details = typeDetails as DetailsTypes.ApiDetailsType
      if (myAndroidVersion == details.androidVersion) {
        val sourceFolder = VfsUtil.findFile(sourcePackage.location, false)
        if (sourceFolder != null && sourceFolder.isValid) {
          return sourceFolder
        }
      }
    }
    return null
  }

  /**
   * Listener that's responsible for closing the generated "no sources available" file when a debug session completes.
   */
  @VisibleForTesting
  internal class MyXDebugSessionListener @VisibleForTesting constructor(fileToClose: VirtualFile, project: Project) :
    XDebugSessionListener {
    private val myFileToClose: WeakReference<VirtualFile>
    private val myProject: Project

    init {
      myFileToClose = WeakReference(fileToClose)
      myProject = project
    }

    override fun sessionStopped() {
      // When debugging is complete, close the generated file that was opened due to debugging into missing sources.
      val file = myFileToClose.get()
      if (file != null) {
        FileEditorManager.getInstance(myProject).closeFile(file)
      }
    }
  }

  companion object {
    @Language("JAVA")
    private val GENERATED_FILE_CONTENTS_FORMAT = Joiner.on(System.lineSeparator()).join(
      "/*********************************************************************",
      " * The Android SDK of the device under debug has API level %d.",
      " * Android SDK source code for this API level cannot be found.",
      " ********************************************************************/"
    )
    private const val GENERATED_FILE_NAME = "Unavailable Source"
    private val LOG = Logger.getInstance(
      AndroidPositionManager::class.java
    )

    @VisibleForTesting
    fun getAndroidVersionFromDebugSession(project: Project): AndroidVersion? {
      val session = XDebuggerManager.getInstance(project).currentSession ?: return null
      return session.debugProcess.processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)
    }

    @VisibleForTesting
    fun getRelPathForJavaSource(project: Project, file: PsiFile): String? {
      val fileType = file.fileType
      if (fileType === JavaFileType.INSTANCE) {
        // When the compile SDK sources are present, they are indexed and the incoming PsiFile is a JavaFileType that refers to them. The
        // relative path for the same file in target SDK sources can be directly determined.
        val fileIndex = ProjectFileIndex.getInstance(project)
        val sourceRoot = fileIndex.getSourceRootForFile(file.virtualFile)
        if (sourceRoot == null) {
          LOG.debug("Could not determine source root for file: " + file.virtualFile.path)
          return null
        }
        return VfsUtilCore.getRelativePath(file.virtualFile, sourceRoot)
      } else if (fileType === JavaClassFileType.INSTANCE) {
        // When the compile SDK sources are not present, the incoming PsiFile is a JavaClassFileType coming from the compile SDK android.jar.
        // We can figure out the relative path to the class file, and make the assumption that the java file will have the same path.
        val virtualFile = file.virtualFile
        val relativeClassPath = VfsUtilCore.getRelativePath(virtualFile, VfsUtilCore.getRootFile(virtualFile))

        // The class file should end in ".class", but we're interested in the corresponding java file.
        return changeClassExtensionToJava(relativeClassPath)
      }
      return null
    }

    @VisibleForTesting
    fun changeClassExtensionToJava(file: String?): String? {
      return if (file != null && file.endsWith(SdkConstants.DOT_CLASS)) {
        file.substring(0, file.length - SdkConstants.DOT_CLASS.length) + SdkConstants.DOT_JAVA
      } else file
    }
  }
}