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
package com.android.tools.idea.structure.services;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class BuildSystemOperationsLookup {
  BuildSystemOperationsLookup() {
  }

  @NotNull
  static DeveloperServiceBuildSystemOperations getBuildSystemOperations(@NotNull Project project) {
    DeveloperServiceBuildSystemOperations found = null;
    for (DeveloperServiceBuildSystemOperations operations : DeveloperServiceBuildSystemOperations.EP_NAME.getExtensions()) {
      if (operations.canHandle(project)) {
        found = operations;
        break;
      }
    }
    assert found != null;
    return found;
  }

  @NotNull
  static DeveloperServiceBuildSystemOperations getBuildSystemOperations(@NotNull String buildSystemId) {
    DeveloperServiceBuildSystemOperations found = null;
    for (DeveloperServiceBuildSystemOperations operations : DeveloperServiceBuildSystemOperations.EP_NAME.getExtensions()) {
      if (operations.getBuildSystemId().equals(buildSystemId)) {
        found = operations;
        break;
      }
    }
    assert found != null;
    return found;
  }
}
