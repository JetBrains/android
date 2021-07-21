/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

// TODO: This is a hotfix for b/194313332 and should be removed eventually.
class ComposeGradleInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    return StudioFlags.COMPOSE_EDITOR_SUPPORT.get() &&
           toolId == "DifferentKotlinGradleVersion" &&
           usesComposeInProject(element.project)
  }

  private fun usesComposeInProject(project: Project): Boolean {
    val modules = ModuleManager.getInstance(project).modules
    return modules.any { it.getModuleSystem().usesCompose }
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}
