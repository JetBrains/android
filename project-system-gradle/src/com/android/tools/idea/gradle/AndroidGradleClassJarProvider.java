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
package com.android.tools.idea.gradle;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency;
import com.android.tools.idea.gradle.model.IdeJavaLibrary;
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.model.ClassJarProvider;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import java.io.File;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Contains Android-Gradle related state necessary for finding a module's external library jars.
 */
public class AndroidGradleClassJarProvider implements ClassJarProvider {

  @Override
  @NotNull
  public List<File> getModuleExternalLibraries(@NotNull Module module) {
    GradleAndroidModel model = GradleAndroidModel.get(module);
    if (model == null) {
      return Lists.transform(AndroidRootUtil.getExternalLibraries(module), VfsUtilCore::virtualToIoFile);
    }

    return Stream.of(
                     model.getSelectedMainRuntimeDependencies().getAndroidLibraries().stream()
                       .map(IdeAndroidLibraryDependency::getTarget)
                       .flatMap(it -> it.getRuntimeJarFiles().stream()),
                     model.getSelectedMainRuntimeDependencies().getJavaLibraries().stream()
                       .map(IdeJavaLibraryDependency::getTarget)
                       .map(IdeJavaLibrary::getArtifact))
      // Flat map the concatenated streams
      .flatMap(s -> s)
      .collect(toImmutableList());
  }
}
