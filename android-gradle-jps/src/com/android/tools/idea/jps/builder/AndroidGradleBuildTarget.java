/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.builder;

import com.android.tools.idea.gradle.compiler.AndroidGradleBuildTargetConstants;
import com.android.tools.idea.jps.AndroidGradleJps;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidGradleBuildTarget extends BuildTarget<AndroidGradleBuildTarget.RootDescriptor> {
  @NonNls private static final String BUILD_TARGET_NAME = "Android Gradle Build Target";

  @NotNull private final JpsProject myProject;

  protected AndroidGradleBuildTarget(@NotNull JpsProject project) {
    super(TargetType.INSTANCE);
    myProject = project;
  }

  @NotNull
  public JpsProject getProject() {
    return myProject;
  }

  @Override
  public String getId() {
    return AndroidGradleBuildTargetConstants.TARGET_ID;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public List<RootDescriptor> computeRootDescriptors(JpsModel model,
                                                     ModuleExcludeIndex index,
                                                     IgnoredFileIndex ignoredFileIndex,
                                                     BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public RootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (RootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return BUILD_TARGET_NAME;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AndroidGradleBuildTarget that = (AndroidGradleBuildTarget)o;
    return myProject.equals(that.myProject);
  }

  @Override
  public int hashCode() {
    return myProject.hashCode();
  }

  public static class TargetType extends BuildTargetType<AndroidGradleBuildTarget> {
    public static final TargetType INSTANCE = new TargetType();

    private TargetType() {
      super(AndroidGradleBuildTargetConstants.TARGET_TYPE_ID);
    }

    @Override
    @NotNull
    public List<AndroidGradleBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      JpsProject project = model.getProject();
      if (!AndroidGradleJps.hasAndroidGradleFacet(project)) {
        return Collections.emptyList();
      }

      return Collections.singletonList(new AndroidGradleBuildTarget(project));
    }

    @Override
    @NotNull
    public BuildTargetLoader<AndroidGradleBuildTarget> createLoader(@NotNull JpsModel model) {
      final JpsProject project = model.getProject();
      return new BuildTargetLoader<AndroidGradleBuildTarget>() {
        @Nullable
        @Override
        public AndroidGradleBuildTarget createTarget(@NotNull String targetId) {
          return AndroidGradleBuildTargetConstants.TARGET_ID.equals(targetId) && AndroidGradleJps.hasAndroidGradleFacet(project)
                 ? new AndroidGradleBuildTarget(project)
                 : null;
        }
      };
    }
  }

  public static class RootDescriptor extends BuildRootDescriptorImpl {
    RootDescriptor(BuildTarget target, File root) {
      super(target, root);
    }
  }
}
