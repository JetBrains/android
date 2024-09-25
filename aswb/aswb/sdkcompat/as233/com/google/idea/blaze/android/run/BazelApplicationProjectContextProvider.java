/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider;
import com.google.idea.blaze.android.projectsystem.BlazeProjectSystem;
import com.google.idea.blaze.android.projectsystem.BlazeToken;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** An implementation of {@link ApplicationProjectContextProvider} for the Blaze project system. */
public class BazelApplicationProjectContextProvider
    implements ApplicationProjectContextProvider<BlazeProjectSystem>, BlazeToken {

  @Nullable
  @Override
  public ApplicationProjectContext computeApplicationProjectContext(
    BlazeProjectSystem projectSystem,
    ApplicationProjectContextProvider.RunningApplicationIdentity identity
  ) {
    String applicationId = identity.getHeuristicApplicationId();
    if (applicationId == null) {
      return null;
    }
    return new BazelApplicationProjectContext(projectSystem.getProject(), applicationId);
  }
}
