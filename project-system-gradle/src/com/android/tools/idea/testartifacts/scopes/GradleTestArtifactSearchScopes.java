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
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.tools.idea.projectsystem.AndroidModuleSystem.Type;
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Gradle implementation of {@link TestArtifactSearchScopes}, differentiates {@code test/} and {@code androidTest/} sources based on
 * information from the model.
 */
public final class GradleTestArtifactSearchScopes implements TestArtifactSearchScopes {
  @NotNull private final Module myModule;

  public GradleTestArtifactSearchScopes(@NotNull Module module) {
    myModule = module;
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
    if (getModuleSystem(myModule).getType() == Type.TYPE_NON_ANDROID) {
      androidTestModule = null;
    }
    else if (getModuleSystem(myModule).getType() == Type.TYPE_TEST) {
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
}
