/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RES_XML
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.util.ReformatUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import java.io.IOException
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.uipreview.EditorUtil
import org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist

/**
 * Quickfix for generating a MotionScene file.
 * <ul>
 * <li>Generate a MotionScene file.</li>
 * <li>Reformat and open the MotionScene file.</li>
 * <li>Set the layoutDescription attribute on the MotionLayout. </li>
 * </ul>
 */
class GenerateMotionSceneFix(val url: ResourceUrl) :
  DefaultLintQuickFix("Generate MotionScene file") {
  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType
  ): Boolean {
    val facet = AndroidFacet.getInstance(startElement) ?: return false
    val appResources = StudioResourceRepositoryManager.getAppResources(facet)
    return !(appResources
      .getResources(ResourceNamespace.TODO(), ResourceType.XML)
      .keySet()
      .contains(url.name))
  }

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context
  ) {
    val project = startElement.project
    val facet = AndroidFacet.getInstance(startElement) ?: return
    WriteCommandAction.runWriteCommandAction(
      project,
      "Create MotionScene file",
      null,
      Runnable {
        try {
          val motionTag = PsiTreeUtil.getNonStrictParentOfType(startElement, XmlTag::class.java)
          val widgetId = findFirstWidgetId(motionTag) ?: "widget"
          @Suppress("DEPRECATION")
          val primaryResourceDir =
            ResourceFolderManager.getInstance(facet).primaryFolder ?: return@Runnable
          val xmlDir = createChildDirectoryIfNotExist(project, primaryResourceDir, FD_RES_XML)
          val resFile = xmlDir.createChildData(project, "${url.name}$DOT_XML")
          VfsUtil.saveText(resFile, generateMotionSceneContent(widgetId))
          ReformatUtil.reformatAndRearrange(project, resFile)
          EditorUtil.openEditor(project, resFile)
          EditorUtil.selectEditor(project, resFile)
          motionTag?.setAttribute(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION, AUTO_URI, url.toString())
        } catch (ex: IOException) {
          val error = String.format("Failed to create file: %1\$s", ex.message)
          Messages.showErrorDialog(project, error, "Create MotionScene")
        }
      }
    )
  }

  private fun findFirstWidgetId(tag: XmlTag?): String? {
    val reference =
      tag?.subTags?.map { it.getAttributeValue(ATTR_ID, ANDROID_URI) }?.find { !it.isNullOrEmpty() }
        ?: return null
    return ResourceUrl.parse(reference)?.name
  }

  @Language("XML")
  private fun generateMotionSceneContent(widgetId: String): String =
    """<?xml version="1.0" encoding="utf-8"?>
<MotionScene
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto">

  <ConstraintSet android:id="@+id/start">
      <Constraint android:id="@+id/$widgetId"/>
  </ConstraintSet>

  <ConstraintSet android:id="@+id/end">
      <Constraint android:id="@id/$widgetId"/>
  </ConstraintSet>

  <Transition
      app:constraintSetStart="@+id/start"
      app:constraintSetEnd="@id/end" />
</MotionScene>
"""
}
