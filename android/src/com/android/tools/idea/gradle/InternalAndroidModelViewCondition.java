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

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

/**
 * Makes "Android Model" tool window available only when {@link IdeaApplication#IDEA_IS_INTERNAL_PROPERTY} is set to true and the project
 * is an Android Gradle project.
 */
public class InternalAndroidModelViewCondition implements Condition<Project> {
  @Override
  public boolean value(@NotNull Project project) {
    return Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY) && Projects.isBuildWithGradle(project);
  }
}
