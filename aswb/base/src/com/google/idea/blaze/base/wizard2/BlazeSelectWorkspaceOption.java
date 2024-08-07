/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard2;

import com.intellij.openapi.options.ConfigurationException;

/**
 * The type of client to be imported. This covers both the build system type and details of the
 * workspace (and optionally, the VCS).
 */
public interface BlazeSelectWorkspaceOption extends BlazeWizardOption {

  @Override
  default void validateAndUpdateBuilder(BlazeNewProjectBuilder builder)
      throws ConfigurationException {
    builder.setWorkspaceData(getWorkspaceData());
  }

  WorkspaceTypeData getWorkspaceData() throws ConfigurationException;
}
