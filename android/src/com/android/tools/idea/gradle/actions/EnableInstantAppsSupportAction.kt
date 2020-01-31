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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys.MODULE
import com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.util.AndroidBundle.message

class EnableInstantAppsSupportAction : AnAction() {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isValidAndroidModuleSelected(e.dataContext)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val module = getSelectedModule(e.dataContext) ?: return
    EnableInstantAppsSupportDialog(module).show()
  }

  private fun isValidAndroidModuleSelected(dataContext: DataContext): Boolean {
    val module = getSelectedModule(dataContext) ?: return false
    return module.androidFacet != null
  }

  private fun getSelectedModule(dataContext: DataContext): Module? {
    val modules = dataContext.getData(MODULE_CONTEXT_ARRAY)
    return if (modules.isNullOrEmpty()) dataContext.getData(MODULE) else modules[0]
  }

  companion object {
    private const val DIST_XMLNS = "xmlns:dist"
    private const val DIST_URI = "http://schemas.android.com/apk/distribution"

    @JvmStatic
    fun addInstantAppSupportToManifest(manifestTag: XmlTag) {
      WriteCommandAction.writeCommandAction(manifestTag.project, manifestTag.containingFile)
        .withName(message("android.wizard.module.enable.instant"))
        .run<RuntimeException> {

          if (manifestTag.getPrefixByNamespace(DIST_URI) == null) { // Add namespace if needed
            manifestTag.setAttribute(DIST_XMLNS, DIST_URI)
          }

          if (manifestTag.findSubTags("module", DIST_URI).isEmpty()) { // Add "dist:module" if needed
            val permissionTag: XmlTag = manifestTag.createChildTag("module", DIST_URI, null, false)
            val manifestPermTag = manifestTag.addSubTag(permissionTag, true)
            manifestPermTag.setAttribute("instant", DIST_URI, "true")

            CodeStyleManager.getInstance(manifestTag.project).reformat(manifestPermTag)
          }
        }
    }
  }
}
