// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.dsl.api.repositories

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.util.GradleVersions
import com.intellij.openapi.project.Project


fun RepositoriesModel.addGoogleMavenRepository(gradleVersion: GradleVersion) {
  if (gradleVersion.compareIgnoringQualifiers("4.0") >= 0) {
    addRepositoryByMethodName(GoogleDefaultRepositoryModel.GOOGLE_METHOD_NAME)
  }
  else {
    addMavenRepositoryByUrl(GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL, GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_NAME)
  }

}

fun RepositoriesModel.addGoogleMavenRepository(project: Project) {
  if (GradleVersions.getInstance().isGradle4OrNewer(project)) {
    this.addRepositoryByMethodName(GoogleDefaultRepositoryModel.GOOGLE_METHOD_NAME)
  }
  else {
    this.addMavenRepositoryByUrl(GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL,
                                 GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_NAME)
  }
}