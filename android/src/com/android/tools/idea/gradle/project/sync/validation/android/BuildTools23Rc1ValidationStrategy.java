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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static java.util.Collections.sort;

class BuildTools23Rc1ValidationStrategy extends AndroidProjectValidationStrategy {
  @NotNull private final List<String> myModules = new ArrayList<>();

  BuildTools23Rc1ValidationStrategy(@NotNull Project project) {
    this(project, module -> {
      GradleBuildModel buildModel = GradleBuildModel.get(module);
      if (buildModel != null) {
        AndroidModel android = buildModel.android();
          return android.buildToolsVersion().toString();
      }
      return null;
    });
  }

  @VisibleForTesting
  BuildTools23Rc1ValidationStrategy(@NotNull Project project, @NotNull BuildToolsVersionReader buildToolsVersionReader) {
    super(project);
  }

  // Build Tools 23 only works with Android plugin 1.3 or newer. Verify that the project is using compatible Build Tools/Android plugin
  // versions.
  @Override
  void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
  }

  @Override
  void fixAndReportFoundIssues() {
    if (!myModules.isEmpty()) {
      sort(myModules);

      StringBuilder msg = new StringBuilder();
      // @formatter:off
      msg.append("Build Tools 23.0.0 rc1 is <b>deprecated</b>.<br>\n")
         .append("Please update these modules to use Build Tools 23.0.0 rc2 (or newer) instead:");
      // @formatter:on

      for (String module : myModules) {
        msg.append("<br>\n * ").append(module);
      }
      msg.append("<br>\n<br>\nOtherwise the project won't build. ");

      Project project = getProject();
      SyncMessage message = new SyncMessage(DEFAULT_GROUP, WARNING, msg.toString());
      GradleSyncMessages.getInstance(project).report(message);
    }
  }

  @VisibleForTesting
  @NotNull
  List<String> getModules() {
    return myModules;
  }

  @VisibleForTesting
  interface BuildToolsVersionReader {
    @Nullable
    String getBuildToolsVersion(@NotNull Module module);
  }
}
