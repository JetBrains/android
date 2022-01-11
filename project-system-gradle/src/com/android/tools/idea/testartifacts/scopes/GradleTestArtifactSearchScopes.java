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
package com.android.tools.idea.testartifacts.scopes;

import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getAndroidTestModule;
import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getMainModule;
import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getUnitTestModule;
import static com.intellij.util.containers.ContainerUtil.map;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gradle implementation of {@link TestArtifactSearchScopes}, differentiates {@code test/} and {@code androidTest/} sources based on
 * information from the model.
 */
public final class GradleTestArtifactSearchScopes implements TestArtifactSearchScopes {
  private static final Key<GradleTestArtifactSearchScopes> SEARCH_SCOPES_KEY = Key.create("TEST_ARTIFACT_SEARCH_SCOPES");

  @NotNull private final Module myModule;
  @NotNull private final GradleAndroidModel myAndroidModel;

  private static final Object ourLock = new Object();

  @Nullable
  public static GradleTestArtifactSearchScopes getInstance(@NotNull Module module) {
    return module.getUserData(SEARCH_SCOPES_KEY);
  }

  /**
   * Initialize the test scopes in the given project.
   */
  public static void initializeScopes(@NotNull Project project) {
    List<Pair<Module, GradleAndroidModel>> models =
      map(ModuleManager.getInstance(project).getModules(), it -> Pair.create(it, GradleAndroidModel.get(it)));

    synchronized (ourLock) {
      for (Pair<Module, GradleAndroidModel> modelPair : models) {
        @NotNull Module module = modelPair.first;
        @Nullable GradleAndroidModel model = modelPair.second;
        module.putUserData(SEARCH_SCOPES_KEY, model == null ? null : new GradleTestArtifactSearchScopes(module, model));
      }
    }
  }

  private GradleTestArtifactSearchScopes(@NotNull Module module, @NotNull GradleAndroidModel androidModel) {
    myModule = module;
    myAndroidModel = androidModel;
  }

  @Override
  public boolean isAndroidTestSource(@NotNull VirtualFile file) {
    return getAndroidTestSourceScope().accept(file);
  }

  @Override
  public boolean isUnitTestSource(@NotNull VirtualFile file) {
    return getUnitTestSourceScope().accept(file);
  }

  @Override
  @NotNull
  public GlobalSearchScope getAndroidTestSourceScope() {
    Module androidTestModule;
    if (myAndroidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_TEST) {
      androidTestModule = getMainModule(myModule);
    }
    else {
      androidTestModule = getAndroidTestModule(myModule);
    }
    return androidTestModule != null ? androidTestModule.getModuleContentScope() : GlobalSearchScope.EMPTY_SCOPE;
  }

  @Override
  @NotNull
  public GlobalSearchScope getUnitTestSourceScope() {
    Module unitTestModule = getUnitTestModule(myModule);
    return unitTestModule != null ? unitTestModule.getModuleContentScope() : GlobalSearchScope.EMPTY_SCOPE;
  }


  @Override
  public String toString() {
    return myModule.getName();
  }
}
