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
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoPackage
import com.android.repository.api.UpdatablePackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider.Companion.REQUIRED_SOURCES_KEY
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.SdkInstallListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [SdkSourceFinderForApiLevel]
 */
class SdkSourceFinderForApiLevelTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project get() = androidProjectRule.project

  private val fileEditorManager by lazy { FileEditorManager.getInstance(project) }

  private var originalLocalPackages: Collection<LocalPackage>? = null

  @After
  fun tearDown() {
    restoreLocalTargetSdkPackages()
  }

  @Test
  fun sourcePosition_targetSourcesAvailable() {
    val target = getFile("android.view.View")

    val finder = SdkSourceFinderForApiLevel(project, apiLevel = 27)

    val sourcePosition = finder.getSourcePosition(target, lineNumber = 12)

    assertThat(sourcePosition.file.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(sourcePosition.file.virtualFile.path).endsWith("android-27/android/view/View.java")
    assertThat(sourcePosition.line).isEqualTo(12)
  }

  @Test
  fun sourcePosition_targetSourcesNotAvailable() {
    val target = getFile("android.view.View")
    removeLocalTargetSdkPackages(30)
    val finder = SdkSourceFinderForApiLevel(project, apiLevel = 30)

    val sourcePosition = finder.getSourcePosition(target, lineNumber = 12)

    assertThat(sourcePosition.file.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(sourcePosition.file.virtualFile.path).isEqualTo("/android-30/UnavailableSource")
    assertThat(sourcePosition.file.virtualFile).isInstanceOf(LightVirtualFile::class.java)
    assertThat(sourcePosition.file.virtualFile.getContent()).contains("device under debug has API level 30.")
    assertThat(sourcePosition.file.name).isEqualTo("android-30/UnavailableSource")
    assertThat(sourcePosition.line).isEqualTo(-1)

    val apiLevel = sourcePosition.file.virtualFile.getUserData(REQUIRED_SOURCES_KEY)!!
    assertThat(apiLevel).isEqualTo(30)
  }

  @Test
  fun sourcePosition_becomesAvailable() {
    val target = getFile("android.view.View")
    removeLocalTargetSdkPackages(28)
    val finder = SdkSourceFinderForApiLevel(project, apiLevel = 28)
    val sourcePosition = finder.getSourcePosition(target, lineNumber = 121)
    assertThat(sourcePosition.file.name).isEqualTo("android-28/UnavailableSource")

    restoreLocalTargetSdkPackages()
    val newSourcePosition = finder.getSourcePosition(target, lineNumber = 121)

    assertThat(newSourcePosition.file.virtualFile.path).endsWith("android-28/android/view/View.java")
    assertThat(newSourcePosition.line).isEqualTo(121)
  }

  @Test
  fun afterDownload_missingSourcesFileDeleted() {
    val target = getFile("android.view.View")
    removeLocalTargetSdkPackages(28)
    val finder = SdkSourceFinderForApiLevel(project, apiLevel = 28)
    val sourcePosition = finder.getSourcePosition(target, lineNumber = 121)
    runInEdtAndWait {
      fileEditorManager.openFile(sourcePosition.file.virtualFile, false)
    }
    val installedPackage = UpdatablePackage(FakeRemotePackage("sources;android-28"))
    assertThat(fileEditorManager.isFileOpen(sourcePosition.file.virtualFile)).isTrue()

    project.messageBus.syncPublisher(SdkInstallListener.TOPIC).installCompleted(listOf(installedPackage), emptyList())

    runInEdt {
      assertThat(fileEditorManager.isFileOpen(sourcePosition.file.virtualFile)).isFalse()
    }
  }

  private fun removeLocalTargetSdkPackages(apiLevel: Int) {
    val packagesToRemove = setOf(
      "${SdkConstants.FD_ANDROID_SOURCES}${RepoPackage.PATH_SEPARATOR}android-$apiLevel",
      "${SdkConstants.FD_PLATFORMS}${RepoPackage.PATH_SEPARATOR}android-$apiLevel"
    )

    val packages = AndroidSdks.getInstance().tryToChooseSdkHandler()
      .getSdkManager(StudioLoggerProgressIndicator(this::class.java))
      .packages
    val localPackages = packages.localPackages.values

    if (originalLocalPackages == null) {
      // This won't get reset at the end of each test automatically. Store original list to restore it later.
      originalLocalPackages = localPackages
    }

    val updatedPackages = localPackages.filter { !packagesToRemove.contains(it.path) }
    packages.setLocalPkgInfos(updatedPackages)
  }

  private fun restoreLocalTargetSdkPackages() {
    if (originalLocalPackages != null) {
      val packages = AndroidSdks.getInstance().tryToChooseSdkHandler()
        .getSdkManager(StudioLoggerProgressIndicator(this::class.java))
        .packages
      packages.setLocalPkgInfos(originalLocalPackages!!)

      originalLocalPackages = null
    }
  }

  @Suppress("SameParameterValue")
  private fun getFile(fqName: String): PsiFile {
    val psiClass = runReadAction { PositionManagerImpl.findClass(project, fqName, GlobalSearchScope.allScope(project), true) }
    return psiClass?.containingFile ?: fail("Failed to get file for $fqName")
  }
}

private fun VirtualFile.getContent(): String = (this as LightVirtualFile).content.toString()