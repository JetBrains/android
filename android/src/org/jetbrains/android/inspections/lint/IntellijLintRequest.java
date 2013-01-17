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
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintRequest;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.List;

class IntellijLintRequest extends LintRequest {
  @NonNull
  private final Project myProject;

  IntellijLintRequest(@NonNull LintClient client, @NonNull List<File> files, @NonNull Project project) {
    super(client, files);
    myProject = project;
  }

  @NonNull
  Project getProject() {
    return myProject;
  }
}
