/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleOutputParserProvider implements ExternalSystemOutputParserProvider {
  @Override
  public ProjectSystemId getExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public List<BuildOutputParser> getBuildOutputParsers(@NotNull ExternalSystemTaskId taskId) {
    Project project = taskId.findProject();
    if (project != null) {
      return project.getService(BuildOutputParserManager.class).getBuildOutputParsers();
    }
    return Collections.emptyList();
  }
}
