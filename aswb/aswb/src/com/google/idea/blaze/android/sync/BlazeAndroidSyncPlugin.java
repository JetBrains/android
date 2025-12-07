/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync;


import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.projectview.AndroidMinSdkSection;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import javax.annotation.Nullable;

/** ASwB sync plugin. */
public class BlazeAndroidSyncPlugin implements BlazeSyncPlugin {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.ANDROID);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.ANDROID;
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return true;
    }

    if (AndroidSdkFromProjectView.getAndroidSdkPlatform(context, project, projectViewSet) == null) {
      return false;
    }
    return true;
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(
        AndroidMinSdkSection.PARSER,
        AndroidSdkPlatformSection.PARSER,
        GeneratedAndroidResourcesSection.PARSER);
  }

  private static boolean isAndroidWorkspace(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID);
  }
}
