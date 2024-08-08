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

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import javax.annotation.Nullable;

/** Provides an option on the "Select .blazeproject" screen */
public interface BlazeSelectProjectViewOption extends BlazeWizardOption {
  /** Returns a shared project view to use */
  @Nullable
  default WorkspacePath getSharedProjectView() {
    return null;
  }

  /** Returns an initial local project view to use */
  @Nullable
  default String getInitialProjectViewText() {
    return null;
  }

  /** Whether to allow the sections to add default values for the project view */
  default boolean allowAddDefaultProjectViewValues() {
    return false;
  }

  /** Returns the directory we're importing from, if applicable. */
  default String getImportDirectory() {
    return null;
  }
}
