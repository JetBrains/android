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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

public class PsProject extends PsModel {
  @NotNull private final Project myProject;

  @NotNull private final List<PsModule> myModules = Lists.newArrayList();

  private boolean myModified;

  public PsProject(@NotNull Project project) {
    super(null);
    myProject = project;

    for (Module resolvedModel : ModuleManager.getInstance(myProject).getModules()) {
      String gradlePath = getGradlePath(resolvedModel);
      if (gradlePath != null) {
        // Only Gradle-based modules are displayed in the PSD.
        PsModule module = null;

        AndroidGradleModel gradleModel = AndroidGradleModel.get(resolvedModel);
        if (gradleModel != null) {
          module = new PsAndroidModule(this, resolvedModel, gradlePath, gradleModel);
        }
        // TODO enable when Java module support is complete.
        //else {
        //  JavaGradleFacet facet = JavaGradleFacet.getInstance(resolvedModel);
        //  if (facet != null) {
        //    JavaProject javaProject = facet.getJavaProject();
        //    if (javaProject != null) {
        //      module = new PsJavaModule(this, resolvedModel, gradlePath, javaProject);
        //    }
        //  }
        //}

        if (module != null) {
          myModules.add(module);
        }
      }
    }
  }

  @Nullable
  public PsModule findModuleByName(@NotNull String moduleName) {
    for (PsModule model : myModules) {
      if (moduleName.equals(model.getName())) {
        return model;
      }
    }
    return null;
  }

  @Nullable
  public PsModule findModuleByGradlePath(@NotNull String gradlePath) {
    for (PsModule model : myModules) {
      if (gradlePath.equals(model.getGradlePath())) {
        return model;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Project getResolvedModel() {
    return myProject;
  }

  @Override
  @NotNull
  public String getName() {
    return myProject.getName();
  }

  public void forEachModule(@NotNull Consumer<PsModule> consumer) {
    myModules.forEach(consumer);
  }

  @Override
  @Nullable
  public PsModel getParent() {
    return null;
  }

  @Override
  public boolean isDeclared() {
    return true;
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void setModified(boolean value) {
    myModified = value;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public int getModelCount() {
    return myModules.size();
  }
}
