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
package com.android.tools.idea.gradle.customizer.cpp;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;

public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<NativeAndroidGradleModel>
  implements BuildVariantModuleCustomizer<NativeAndroidGradleModel> {

  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                                @NotNull NativeAndroidGradleModel nativeAndroidGradleModel) {
    return Lists.newArrayList(moduleModel.addContentEntry(pathToIdeaUrl(nativeAndroidGradleModel.getRootDirPath())));
  }

  @Override
  protected void setUpContentEntries(@NotNull ModifiableRootModel ideaModuleModel,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull NativeAndroidGradleModel nativeAndroidGradleModel,
                                     @NotNull List<RootSourceFolder> orphans) {
    Collection<File> sourceFolders = nativeAndroidGradleModel.getSelectedVariant().getSourceFolders();
    if (!sourceFolders.isEmpty()) {
      addSourceFolders(contentEntries, sourceFolders, SOURCE, orphans, false);
    }
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> sourceDirPaths,
                                @NotNull JpsModuleSourceRootType type,
                                @NotNull List<RootSourceFolder> orphans,
                                boolean generated) {
    for (File path : sourceDirPaths) {
      addSourceFolder(contentEntries, path, type, generated, orphans);
    }
  }

  @NotNull
  @Override
  public ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @NotNull
  @Override
  public Class<NativeAndroidGradleModel> getSupportedModelType() {
    return NativeAndroidGradleModel.class;
  }
}
