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
package com.android.tools.idea.gradle;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;

/**
 * Common project entity {@link Key keys}.
 */
public class AndroidProjectKeys {
  @NotNull public static final Key<IdeaAndroidProject> IDE_ANDROID_PROJECT =
    Key.create(IdeaAndroidProject.class, ProjectKeys.PROJECT.getProcessingWeight() + 5);

  @NotNull public static final Key<IdeaModule> IDEA_MODULE = Key.create(IdeaModule.class, ProjectKeys.MODULE.getProcessingWeight() + 5);

  @NotNull public static final Key<ProjectImportEventMessage> IMPORT_EVENT_MSG =
    Key.create(ProjectImportEventMessage.class, IDE_ANDROID_PROJECT.getProcessingWeight() + 5);

  @NotNull public static final Key<IdeaGradleProject> IDE_GRADLE_PROJECT =
    Key.create(IdeaGradleProject.class, IDE_ANDROID_PROJECT.getProcessingWeight() + 10);

  private AndroidProjectKeys() {
  }
}
