/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.EditorUtil.openEditor
import com.android.tools.idea.util.EditorUtil.reformatAndRearrange
import com.android.tools.idea.util.EditorUtil.selectEditor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager.Companion.getInstance
import org.jetbrains.android.util.AndroidUtils
import java.io.IOException

/**
 * Quickfix for generating a media capabilities descriptor XML file.
 *
 *  * Generate a blank descriptor, with some commented out formats.
 *  * Reformat and open the generated descriptor.
 *
 */
internal class GenerateMediaCapabilitiesDescriptorFix(private val myUrl: ResourceUrl) : LintIdeQuickFix {
  override fun apply(startElement: PsiElement,
                     endElement: PsiElement,
                     context: AndroidQuickfixContexts.Context) {
    val project = startElement.project
    val facet = AndroidFacet.getInstance(startElement) ?: return
    WriteCommandAction.runWriteCommandAction(project, "Create Media Capabilities Descriptor", null, {
      try {
        val primaryResourceDir = getInstance(facet).primaryFolder!!
        val xmlDir = AndroidUtils.createChildDirectoryIfNotExist(project, primaryResourceDir, SdkConstants.FD_RES_XML)
        val resFile = xmlDir.createChildData(project, myUrl.name + SdkConstants.DOT_XML)
        VfsUtil.saveText(resFile, XML_CONTENT)
        reformatAndRearrange(project, resFile)
        openEditor(project, resFile)
        selectEditor(project, resFile)
      }
      catch (e: IOException) {
        val error = String.format("Failed to create file: %1\$s", e.message)
        Messages.showErrorDialog(project, error, "Create Media Capabilities Resource")
      }
    })
  }

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean {
    val facet = AndroidFacet.getInstance(startElement)
    val appResources = if (facet == null) null else ResourceRepositoryManager.getAppResources(facet)
    return appResources == null || !(appResources.hasResources(ResourceNamespace.TODO(), ResourceType.XML, myUrl.name))
  }

  override fun getName(): String {
    return "Generate media capabilities descriptor"
  }

  companion object {
    /** These come from frameworks/base/media/java/android/media/ApplicationMediaCapabilities.java # parseFormatTag */
    private val MEDIA_FORMATS = arrayOf("HEVC", "HDR10", "HDR10Plus", "Dolby-Vision", "HLG", "SlowMotion")

    private val XML_CONTENT = """
      <?xml version="1.0" encoding="utf-8"?>
      <media-capabilities xmlns:android="http://schemas.android.com/apk/res/android">
          <!-- TODO Uncomment the following lines to let the Android OS
           know that the given media format is not supported by the app
           and will need to be transcoded. -->
          ${MEDIA_FORMATS.joinToString("\n") { mediaFormat -> "<!--<format android:name=\"$mediaFormat\" supported=\"false\"/>-->" }}
      </media-capabilities>
    """.trimIndent()
  }
}