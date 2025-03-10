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
package com.android.tools.idea.lint.inspections

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.lint.detector.api.LintFix
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

private const val minSdkUpgradeAsstVersion = 26
private val maxSdkUpgradeAsstVersion
  get() = if (StudioFlags.TSDKVUA_API_35.get()) 34 else 33

class LaunchTargetSdkVersionAssistantFix(fix: LintFix?) :
  DefaultLintQuickFix("Launch Android SDK Upgrade Assistant") {

  // -1 if there is no valid current target SDK version for some reason
  private val tsdkv: Int = fix?.let { LintFix.getInt(it, "currentTargetSdkVersion", -1) } ?: -1

  private val sdkUpgradeAssistantHasSupport: Boolean
    get() = tsdkv in minSdkUpgradeAsstVersion..maxSdkUpgradeAsstVersion

  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType,
  ): Boolean = IdeInfo.getInstance().isAndroidStudio && sdkUpgradeAssistantHasSupport

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context,
  ) {
    stopFlaggingTargetSdkEditsForSession(startElement.project)
    OpenAssistSidePanelAction()
      .openWindow("DeveloperServices.TargetSDKVersionUpgradeAssistant", startElement.project)
  }

  override fun generatePreview(
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}
