// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.navigation

import com.android.SdkConstants.EXT_GRADLE_DECLARATIVE
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.plugins.gradle.config.GradleBuildscriptSearchScope
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.toml.lang.psi.TomlElement

/**
 * This is a copy from JetBrains TOML navigation fix
 * https://github.com/JetBrains/intellij-community/commit/ae5d725ec6e24c00414efdf79b774306442720c2.
 */
class GradleTomlUseScopeEnlarger: UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element !is TomlElement) return null
    val containingFile = element.containingFile?.virtualFile ?: return null
    val versionCatalogFiles = getVersionCatalogFiles(element.project).values
    if (containingFile !in versionCatalogFiles) {
      return null
    }
    val gradleBuildscriptSearchScope = GradleBuildscriptSearchScope(element.project)
    if (!StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get()) return gradleBuildscriptSearchScope
    return GlobalSearchScope.union(listOf(gradleBuildscriptSearchScope, object : GlobalSearchScope(element.project) {
      override fun contains(file: VirtualFile) = file.name.endsWith(EXT_GRADLE_DECLARATIVE)
      override fun isSearchInLibraries() = false
      override fun isSearchInModuleContent(aModule: Module) = true
      override fun getDisplayName() = "Gradle Declarative Configuration Files"
    }))
  }
}