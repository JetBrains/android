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
package com.android.tools.idea.gradle.stubs.gradle;

import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;

public class GradleTaskStub implements GradleTask {
  @NotNull private final String myName;
  @NotNull private final GradleProjectStub myProject;

  GradleTaskStub(@NotNull String name, @NotNull GradleProjectStub project) {
    myName = name;
    myProject = project;
  }

  @Override
  public String getPath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public ProjectIdentifier getProjectIdentifier() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDisplayName() {
    return myName;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public GradleProjectStub getProject() {
    return myProject;
  }

  @Override
  public String getGroup() {
    return null;
  }

  @Override
  public boolean isPublic() {
    return true;
  }
}
