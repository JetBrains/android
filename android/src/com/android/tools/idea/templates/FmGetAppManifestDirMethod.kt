/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.templates.TemplateAttributes.ATTR_PROJECT_OUT
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.LocalFileSystem
import freemarker.template.TemplateMethodModelEx
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProviderManager

import java.io.File

class FmGetAppManifestDirMethod(private val myParamMap: Map<String, Any>) : TemplateMethodModelEx {
  override fun exec(arguments: List<*>): Any? {
    val module = findAppModuleIfAny() ?: return null
    val facet = AndroidFacet.getInstance(module) ?: return null
    val provider = SourceProviderManager.getInstance(facet).mainIdeaSourceProvider
    val file = provider.manifestFile ?: return null

    return file.parent.canonicalPath
  }

  private fun findAppModuleIfAny(): Module? {
    val appName = "app"
    val mobileName = "mobile"
    val modulePath = myParamMap[ATTR_PROJECT_OUT] as? String ?: return null
    val file = LocalFileSystem.getInstance().findFileByIoFile(File(modulePath.replace('/', File.separatorChar))) ?: return null
    val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return null
    val manager = ModuleManager.getInstance(project)
    val module = manager.findModuleByName(appName)
    return module ?: manager.findModuleByName(mobileName)
  }
}
