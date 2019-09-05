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
package com.android.tools.idea.gradle.stubs.gradle;

import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ProjectIdentifierStub implements ProjectIdentifier {
  @NotNull private final BuildIdentifierStub myBuildIdentifier;

  ProjectIdentifierStub(@NotNull File rootDir) {
    myBuildIdentifier = new BuildIdentifierStub(rootDir);
  }

  @Override
  public String getProjectPath() {
    return myBuildIdentifier.getRootDir().getPath();
  }

  @Override
  public BuildIdentifier getBuildIdentifier() {
    return myBuildIdentifier;
  }
}
