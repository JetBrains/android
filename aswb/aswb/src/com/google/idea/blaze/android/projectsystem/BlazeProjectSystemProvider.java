/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;

/**
 * A BlazeProjectSystemProvider determines whether or not a BazelProjectSystem would be applicable
 * for a given project. If so, the provider is responsible for creating the instance of
 * BazelProjectSystem that should be associated with the project.
 *
 * <p>We provide this functionality in BlazeProjectSystemProvider instead of having
 * BazelProjectSystem implement AndroidProjectSystemProvider itself because there are times when we
 * want to instantiate the provider extension without creating a new instance of the project system.
 * In particular, the provider extension may be instantiated after the disposal of the project, in
 * which case we can't create the project system because it interacts with the project during
 * instantiation.
 */
public class BlazeProjectSystemProvider implements AndroidProjectSystemProvider {
  public static final String ID = "com.google.idea.blaze.BazelProjectSystem";

  @Override
  public boolean isApplicable(Project project) {
    return Blaze.isBlazeProject(project);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public AndroidProjectSystem projectSystemFactory(Project project) {
    return new BazelProjectSystem(project);
  }
}
