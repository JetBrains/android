// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.importing

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter.Companion.ANDROID_PROJECT_TYPE
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectType
import com.intellij.openapi.project.ProjectTypesProvider
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.ProjectScope.getLibrariesScope

internal class AndroidProjectTypesProvider : ProjectTypesProvider {
  override fun inferProjectTypes(project: Project): Collection<ProjectType> {
    if (JavaPsiFacade.getInstance(project).findClass("android.app.Activity", getLibrariesScope(project)) != null) {
      return listOf(ANDROID_PROJECT_TYPE)
    }
    return emptyList()
  }
}