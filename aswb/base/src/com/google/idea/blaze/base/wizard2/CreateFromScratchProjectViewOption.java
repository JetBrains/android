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

import javax.annotation.Nullable;
import javax.swing.JComponent;

/** Creates an empty project view */
public class CreateFromScratchProjectViewOption implements BlazeSelectProjectViewOption {
  @Override
  public String getOptionName() {
    return "create-from-scratch";
  }

  @Override
  public String getDescription() {
    return "Create from scratch";
  }

  @Override
  public JComponent getUiComponent() {
    return null;
  }

  @Nullable
  @Override
  public String getInitialProjectViewText() {
    return "";
  }

  @Override
  public boolean allowAddDefaultProjectViewValues() {
    return true;
  }

  @Override
  public void commit() {}

  @Override
  public void validateAndUpdateBuilder(BlazeNewProjectBuilder builder) {}
}
