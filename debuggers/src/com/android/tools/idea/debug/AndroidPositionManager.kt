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
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.SdkInstallListener
import com.android.tools.idea.sdk.sources.SdkSourcePositionFinder
import com.google.common.annotations.VisibleForTesting
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebugSessionListener
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import java.lang.ref.WeakReference

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
class AndroidPositionManager(private val myDebugProcess: DebugProcessImpl) : PositionManagerImpl(myDebugProcess) {

  private val myAndroidVersion: AndroidVersion? = myDebugProcess.processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)

  init {
    val disposable = myDebugProcess.getDisposable()
    if (disposable == null) {
      thisLogger().warn("Cannot subscribe to OnDownloadedCallback")
    }
    else {
      myDebugProcess.project.messageBus.connect(disposable)
        .subscribe(SdkInstallListener.TOPIC, SdkInstallListener { installed, uninstalled ->
          val path = DetailsTypes.getSourcesPath(myAndroidVersion ?: return@SdkInstallListener)
          if (installed.find { it.path == path } != null || uninstalled.find { it.path == path } != null) {
            refreshDebugSession()
          }
        })
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
  override fun createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<ClassPrepareRequest> {
    // For desugaring, we also need to add prepare requests for the extra synthesized classes that may contain the source position.
    val requests =
      DesugarUtils.addExtraPrepareRequestsIfNeeded(myDebugProcess, requestor, position, super.createPrepareRequests(requestor, position))
    if (requests.isEmpty()) {
      throw NoDataException.INSTANCE
    }
    return requests
  }

  // When setting breakpoints or debugging into SDK source, the Location's sourceName() method
  // returns a string of the form "FileName.java"; this resolves into a JavaFileType.
  override fun getAcceptedFileTypes(): Set<FileType> = setOf(JavaFileType.INSTANCE)

  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    if (location == null) throw NoDataException.INSTANCE

    if (myAndroidVersion == null) {
      LOG.debug("getSourcePosition cannot determine version from device.")
      throw NoDataException.INSTANCE
    }

    val project = myDebugProcess.project
    val file = getPsiFileByLocation(project, location)
    if (file == null || !AndroidSdks.getInstance().isInAndroidSdk(file)) throw NoDataException.INSTANCE

    // Since we have an Android SDK file, return the SDK source if it's available.
    // Otherwise, return a generated file with a comment indicating that sources are unavailable.
    return SdkSourcePositionFinder.getInstance(project).getSourcePosition(myAndroidVersion.apiLevel, file, location.lineNumber())
  }

  // This override only exists for the purpose of changing visibility for invocation via tests.
  @VisibleForTesting
  public override fun getPsiFileByLocation(project: Project, location: Location): PsiFile? = super.getPsiFileByLocation(project, location)

  /**
   * Listener that's responsible for closing the generated "no sources available" file when a debug session completes.
   */
  @VisibleForTesting
  class MyXDebugSessionListener @VisibleForTesting constructor(fileToClose: VirtualFile, project: Project) :
    XDebugSessionListener {

    private val myFileToClose = WeakReference(fileToClose)
    private val myProject = project

    override fun sessionStopped() {
      // When debugging is complete, close the generated file that was opened due to debugging into missing sources.
      myFileToClose.get()?.let { runInEdt { FileEditorManager.getInstance(myProject).closeFile(it) } }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(AndroidPositionManager::class.java)

    @VisibleForTesting
    fun getRelPathForJavaSource(project: Project, file: PsiFile): String? {
      return when (file.fileType) {
        JavaFileType.INSTANCE -> {
          // When the compile SDK sources are present, they are indexed and the incoming PsiFile is a JavaFileType that refers to them. The
          // relative path for the same file in target SDK sources can be directly determined.
          val sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(file.virtualFile)
          if (sourceRoot == null) {
            LOG.debug("Could not determine source root for file: " + file.virtualFile.path)
            null
          }
          else {
            VfsUtilCore.getRelativePath(file.virtualFile, sourceRoot)
          }
        }

        JavaClassFileType.INSTANCE -> {
          // When the compile SDK sources are not present, the incoming PsiFile is a JavaClassFileType coming from the compile SDK
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

    @VisibleForTesting
    fun String.changeClassExtensionToJava() =
      if (endsWith(SdkConstants.DOT_CLASS)) substring(0, length - SdkConstants.DOT_CLASS.length) + SdkConstants.DOT_JAVA else this
  }

  private fun refreshDebugSession() {
    DumbService.getInstance(myDebugProcess.project).smartInvokeLater {
      myDebugProcess.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
        // Clear the cache on the containing CompoundPositionManager.
        myDebugProcess.positionManager.clearCache()

        // After the cache is cleared, close the generated PsiFile instance if it's open and schedule a refresh of the debug session.
        ApplicationManager.getApplication().invokeLater(
          { myDebugProcess.session.refresh(true) },
          { myDebugProcess.session.isStopped })
      }
    }

  }

  // TODO(b/269626310): Remove when DebugProcessImpl exposes a disposable
  //   https://github.com/JetBrains/intellij-community/pull/2326
  private fun DebugProcessImpl.getDisposable(): Disposable? {
    return when {
      ApplicationManager.getApplication().isUnitTestMode -> project
      else -> try {
        val field = DebugProcessImpl::class.java.getDeclaredField("myDisposable")
        field.isAccessible = true
        field.get(this) as Disposable
      }
      catch (e: Exception) {
        thisLogger().warn("Could not get DebugProcessImpl.disposable")
        null
      }
    }
  }
}
