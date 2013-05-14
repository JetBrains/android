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

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;

public class GradleProjectStub implements GradleProject {
  @NotNull private final String myPath;

  public GradleProjectStub(@NotNull String path) {
    myPath = path;
  }

  @Override
  public DomainObjectSet<? extends GradleTask> getTasks() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GradleProject getParent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DomainObjectSet<? extends GradleProject> getChildren() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getPath() {
    return myPath;
  }

  @Override
  public GradleProject findByPath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDescription() {
    throw new UnsupportedOperationException();
  }
}
