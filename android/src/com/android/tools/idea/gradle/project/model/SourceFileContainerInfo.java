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
package com.android.tools.idea.gradle.project.model;

import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Variant;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;

public class SourceFileContainerInfo {
  @Nullable public final Variant variant;
  @Nullable public final BaseArtifact artifact;

  SourceFileContainerInfo() {
    this(null);
  }

  SourceFileContainerInfo(@Nullable Variant variant) {
    this(variant, null);
  }

  SourceFileContainerInfo(@Nullable Variant variant, @Nullable BaseArtifact artifact) {
    this.variant = variant;
    this.artifact = artifact;
  }

  public void updateSelectedVariantIn(@NotNull DataNode<ModuleData> moduleNode) {
    if (variant != null) {
      DataNode<AndroidModuleModel> androidProjectNode = find(moduleNode, ANDROID_MODEL);
      if (androidProjectNode != null) {
        androidProjectNode.getData().setSelectedVariantName(variant.getName());
      }
    }
  }
}
