/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.android.tools.idea.projectsystem.ApplicationProjectContext;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.intellij.openapi.project.Project;
import java.util.function.Supplier;

/**
 * An implementation of {@link ApplicationProjectContext} used in the Blaze project system.
 *
 * <p><b>Note:</b> The Blaze project system assumes all instances of the {@link
 * ApplicationProjectContext} associated with its projects to be backed by this specific class.
 */
public class BazelApplicationProjectContext implements ApplicationProjectContext {

  private final Project project;
  private final Supplier<String> applicationId;

  public BazelApplicationProjectContext(Project project, String applicationId) {
    this.project = project;
    this.applicationId = () -> applicationId;
  }

  public BazelApplicationProjectContext(
      Project project, ApplicationIdProvider applicationIdProvider) {
    this.project = project;
    this.applicationId =
        () -> {
          try {
            return applicationIdProvider.getPackageName();
          } catch (ApkProvisionException e) {
            throw new RuntimeException(e);
          }
        };
  }

  public Project getProject() {
    return project;
  }

  @Override
  public String getApplicationId() {
    return applicationId.get();
  }
}
