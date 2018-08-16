/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.tools.idea.flags.StudioFlags.NELE_USE_ANDROIDX_DEFAULT
import com.android.tools.idea.util.dependsOnOldSupportLib
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.LocalFileSystem
import freemarker.template.TemplateBooleanModel
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException
import org.jetbrains.android.refactoring.hasAndroidxProperty
import org.jetbrains.android.refactoring.isAndroidx
import java.io.File

/**
 * Method invoked by FreeMarker to check if AndroidX mapping should be enabled. It has no parameters.
 */
class FmIsAndroidxEnabledMethod(private val paramMap: Map<String, Any>) : TemplateMethodModelEx {

  private fun findProjectIfAny(): Project? {
    val modulePath = paramMap[TemplateMetadata.ATTR_PROJECT_OUT] as? String
    return modulePath?.let {
      val file = LocalFileSystem.getInstance().findFileByIoFile(File(modulePath.replace('/', File.separatorChar))) ?: return null
      ProjectLocator.getInstance().guessProjectForFile(file)
    }
  }

  private fun findModuleIfAny(): Module? {
    val modulePath = paramMap[TemplateMetadata.ATTR_PROJECT_OUT] as? String
    return modulePath?.let {
      FmUtil.findModule(modulePath)
    }
  }

  @Throws(TemplateModelException::class)
  override fun exec(args: List<*>): TemplateModel {
    val buildApiObject = (paramMap.get(TemplateMetadata.ATTR_BUILD_API) as? Int) ?: 0

    if (buildApiObject < 28) {
      // androidx is not supported for <28
      return TemplateBooleanModel.FALSE
    }

    val project = findProjectIfAny()
    val useAndroidx = when {
      // Don't use AndroidX if the module has old support library dependencies
      findModuleIfAny()?.dependsOnOldSupportLib() == true -> false

      // If the project already has the "useAndroidx" property set, just do what the property says
      project?.hasAndroidxProperty() == true -> project.isAndroidx()

      // Default based on the global flag
      else -> NELE_USE_ANDROIDX_DEFAULT.get()
    }

    return if (useAndroidx) TemplateBooleanModel.TRUE else TemplateBooleanModel.FALSE
  }
}