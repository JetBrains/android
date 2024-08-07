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
package com.google.idea.blaze.base.run.state;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/** Supports managing part of a run configuration's state. */
public interface RunConfigurationState {

  /** Loads this handler's state from the external data. */
  void readExternal(Element element) throws InvalidDataException;

  /** Updates the element with the handler's state. */
  @SuppressWarnings("ThrowsUncheckedException")
  void writeExternal(Element element) throws WriteExternalException;

  /** @return A {@link RunConfigurationStateEditor} for this state. */
  RunConfigurationStateEditor getEditor(Project project);
}
