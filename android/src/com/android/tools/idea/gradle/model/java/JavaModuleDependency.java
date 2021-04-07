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
package com.android.tools.idea.gradle.model.java;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.intellij.serialization.PropertyMapping;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dependency to a Java module.
 */
public class JavaModuleDependency implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private final String myModuleName;
  @NotNull private final String myModuleId;
  @Nullable private final String myScope;
  private final boolean myExported;

  @Nullable
  public static JavaModuleDependency copy(IdeaProject project, IdeaModuleDependency original) {
    IdeaModule targetModule = null;
    for (IdeaModule module : project.getModules()) {
      if (module.getName().equals(original.getTargetModuleName())) {
         targetModule= module;
      }
    }

    if (targetModule != null && isNotEmpty(targetModule.getName())) {
      String scope = null;
      IdeaDependencyScope originalScope = original.getScope();
      if (originalScope != null) {
        scope = originalScope.getScope();
      }
      GradleProject gradleProject = targetModule.getGradleProject();

      File projectFolder;
      try {
        projectFolder = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir();
      }
      catch (UnsupportedMethodException ex) {
        // Old version of Gradle doesn't support getProjectIdentifier, find folder path of the root project.
        GradleProject rootGradleProject = gradleProject;
        while (rootGradleProject.getParent() != null) {
          rootGradleProject = rootGradleProject.getParent();
        }
        projectFolder = rootGradleProject.getProjectDirectory();
      }
      String moduleId = createUniqueModuleId(projectFolder, gradleProject.getPath());
      return new JavaModuleDependency(targetModule.getName(), moduleId, scope, original.getExported());
    }
    return null;
  }

  @PropertyMapping({
    "myModuleName",
    "myModuleId",
    "myScope",
    "myExported"
  })
  public JavaModuleDependency(@NotNull String moduleName, @NotNull String moduleId, @Nullable String scope, boolean exported) {
    myModuleName = moduleName;
    myModuleId = moduleId;
    myScope = scope;
    myExported = exported;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getModuleId() {
    return myModuleId;
  }

  @Nullable
  public String getScope() {
    return myScope;
  }

  public boolean isExported() {
    return myExported;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      myModuleName,
      myModuleId,
      myScope,
      myExported
    );
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof JavaModuleDependency)) {
      return false;
    }
    JavaModuleDependency dependency = (JavaModuleDependency) obj;
    return Objects.equals(myModuleName, dependency.myModuleName)
           && Objects.equals(myModuleId, dependency.myModuleId)
           && Objects.equals(myScope, dependency.myScope)
           && Objects.equals(myExported, dependency.myExported);
  }
}
