/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.tools.idea.projectsystem.SourceProviderManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.android.facet.AndroidFacet

private const val ALLOW_BACKUP = "android:allowBackup"

class ManifestPatcher(private val project: Project) {
  private val moduleManager = ModuleManager.getInstance(project)
  private val psiManager = PsiManager.getInstance(project)

  fun enableBackup(applicationId: String): Boolean {
    val manifests = findManifests(applicationId)
    manifests.forEach {
      val application = it.rootTag?.findFirstSubTag("application") ?: return@forEach
      val attribute = application.getAttribute(ALLOW_BACKUP) ?: return@forEach
      if (attribute.value != "true") {
        WriteCommandAction.runWriteCommandAction(project) { attribute.setValue("true") }
        PsiNavigateUtil.navigate(attribute, true)
        return true
      }
    }
    return false
  }

  private fun findManifests(applicationId: String): List<XmlFile> {
    val module = project.findModule(applicationId) ?: return emptyList()
    val modules = buildList {
      add(module)
      addAll(project.modules.filter { moduleManager.isModuleDependent(module, it) })
    }
    return modules.flatMap { it.getManifests() }.mapNotNull { psiManager.findFile(it) as? XmlFile }
  }
}

private fun Module.getManifests(): Iterable<VirtualFile> {
  val facet = AndroidFacet.getInstance(this) ?: return emptyList()
  val sourceProvider = SourceProviderManager.getInstance(facet)
  return sourceProvider.sources.manifestFiles
}
