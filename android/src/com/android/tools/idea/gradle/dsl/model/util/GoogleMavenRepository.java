/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.util;

import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.*;

/**
 * Tools to handle Google Maven Repository
 */
public class GoogleMavenRepository {
  /**
   * Look for Google Maven repository on a RepositoriesModel. If Gradle version is 4 or newer, look for it by method call and url.
   * If it is lower than 4, look only by url.
   *
   * @param repositoriesModel Instance of RepositoriesModel in where to look for.
   * @return {@code true} if Google Maven repository can be found in {@code repositoriesModel}, {@code false} otherwise.
   */
  public static boolean hasGoogleMavenRepository(@NotNull RepositoriesModel repositoriesModel) {
    GroovyPsiElement psiElement = repositoriesModel.getPsiElement();
    if (psiElement == null) {
      // No psiElement means that there is no repository block
      return false;
    }
    Project project = repositoriesModel.getPsiElement().getProject();
    if (GradleVersions.getInstance().isGradle4OrNewer(project) && repositoriesModel.containsMethodCall(GOOGLE_METHOD_NAME)) {
      // google repository by method can only be used in gradle 4.0+
      return true;
    }
    return repositoriesModel.containsMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL);
  }

  public static void addGoogleRepository(@NotNull RepositoriesModel repositoriesModel, @NotNull Project project) {
    if (GradleVersions.getInstance().isGradle4OrNewer(project)) {
      repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    }
    else {
      repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    }
  }
}
