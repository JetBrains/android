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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

abstract class AddDependencyPanel extends JPanel {
  @NotNull private final String myDescription;
  @NotNull private final Icon myIcon;

  AddDependencyPanel(@NotNull String description, @NotNull Icon icon) {
    super(new BorderLayout());
    this.myDescription = description;
    this.myIcon = icon;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }
}
