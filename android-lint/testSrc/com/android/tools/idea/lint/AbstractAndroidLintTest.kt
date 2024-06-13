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
package com.android.tools.idea.lint

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.LintExternalAnnotator
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.lint.client.api.LintClient
import com.android.tools.tests.AdtTestProjectDescriptors
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class AbstractAndroidLintTest : AndroidTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  public override fun setUp() {
    myProjectDescriptor = AdtTestProjectDescriptors.kotlin()
    super.setUp()

    // Create the iml file for a module on disk. This is necessary for correct Kotlin resolution of
    // light classes,
    // see AndroidResolveScopeEnlarger.
    if (!SystemInfo.isWindows) {
      VfsTestUtil.createFile(
        LocalFileSystem.getInstance().findFileByPath("/")!!,
        myModule.moduleFilePath,
      )
    }

    val analyticsSettings = AnalyticsSettingsData()
    analyticsSettings.optedIn = false
    AnalyticsSettings.setInstanceForTest(analyticsSettings)
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
  }

  override fun getLanguageLevel(): LanguageLevel? {
    // Higher language levels trigger JavaPlatformModuleSystem checks which fail for our light PSI
    // classes. For now set the language level
    // to what real AS actually uses.
    // TODO(b/110679859): figure out how to stop JavaPlatformModuleSystem from thinking the light
    // classes are not accessible.
    return LanguageLevel.JDK_1_8
  }

  protected fun doTestNoFix(
    inspection: AndroidLintInspectionBase,
    copyTo: String,
    extension: String,
  ) {
    doTestHighlighting(inspection, copyTo, extension)
    var action: IntentionAction? = null
    for (a in myFixture.availableIntentions) {
      if (a is LintExternalAnnotator.MyFixingIntention) {
        action = a
      }
    }
    assertNull(action)
  }

  protected fun doTestWithFix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String,
  ) {
    val action = doTestHighlightingAndGetQuickfix(inspection, message, copyTo, extension)
    doTestWithAction(extension, action)
  }

  private fun doTestWithAction(extension: String, action: IntentionAction) {
    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    // TODO(b/319287252): Remove write command after migration to ModCommand API is complete.
    if (action.asModCommandAction() != null) {
      myFixture.checkPreviewAndLaunchAction(action)
    } else {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        action.invoke(myFixture.project, myFixture.editor, myFixture.file)
      }
    }

    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after." + extension)
  }

  private fun doTestHighlightingAndGetQuickfix(
    inspection: AndroidLintInspectionBase,
    message: String,
    copyTo: String,
    extension: String,
  ): IntentionAction {
    doTestHighlighting(inspection, copyTo, extension)
    return myFixture.getIntentionAction(message)
      ?: error(
        "Couldn't find intention action \"$message\"; options were:\n${myFixture.availableIntentions.joinToString("\n") { it.text }}"
      )
  }

  protected fun doTestHighlighting(
    inspection: AndroidLintInspectionBase,
    copyTo: String,
    extension: String,
  ) {
    myFixture.enableInspections(inspection)
    val file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo)
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.checkHighlighting(true, false, false)
  }

  protected fun collectTestHighlighting(
    inspection: AndroidLintInspectionBase,
    copyTo: String,
    extension: String,
  ): List<HighlightInfo> {
    myFixture.enableInspections(inspection)
    val file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + "." + extension, copyTo)
    myFixture.configureFromExistingVirtualFile(file)
    return myFixture.doHighlighting()
  }

  companion object {
    @NonNls const val BASE_PATH = "/lint/"
    @NonNls const val BASE_PATH_GLOBAL = BASE_PATH + "global/"
  }
}
