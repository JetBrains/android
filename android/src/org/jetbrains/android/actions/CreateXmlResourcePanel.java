/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.actions;

import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Interface: what a create xml values resource file panel should provide.
 * This is usually embedded in other UI, so exposes a wider interface.
 */
public interface CreateXmlResourcePanel {

  /**
   * @return the name of the new resource
   */
  String getResourceName();

  /**
   * @return the type of the new resource
   */
  ResourceType getType();

  /**
   * @return the value of the new resource
   */
  String getValue();

  /**
   * @return the base res/ directory to place the new file (or where an existing file would sit)
   *         Null if the user did not supply sufficient information to determine the res/ directory.
   *         For example, if the user did not select a module with resources, or select a resource directory in the UI.
   */
  @Nullable
  VirtualFile getResourceDirectory();

  /**
   * @return the list of subdirectories (different configurations) to place the resource value
   */
  List<String> getDirNames();

  /**
   * @return the name of the resource file that holds the new value
   */
  String getFileName();

  /**
   * Resets the panel to the same state as after it was created.
   */
  void resetToDefault();

  /**
   * Reset the panel to a state as if the given file was the initial default file for placing the new value.
   */
  void resetFromFile(@NotNull VirtualFile file, @NotNull Project project);

  /**
   * Validate the user entries in the UI.
   * @return null if user data is valid, otherwise return the reason it is invalid
   */
  @Nullable
  ValidationInfo doValidate();

  /**
   * Return a resource name validator for the given scope. The UI may optionally allow a user to change scope.
   * @return a resource name validator
   */
  IdeResourceNameValidator getResourceNameValidator();

  /**
   * Return a module that represents the scope of the resources for this panel.
   * (may or may not be a user choice)
   * @return the module
   */
  Module getModule();

  JComponent getPreferredFocusedComponent();

  JComponent getPanel();

  /** Sets whether the resource value field is enabled (editable). */
  void setAllowValueEditing(boolean enabled);
}
