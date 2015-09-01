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
package com.android.tools.idea.gradle.structure.editors;

import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.structure.EditorPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for Project Structure Android-Gradle module property panels that generically edit various build.gradle properties.
 */
public abstract class BuildFilePanel extends EditorPanel {
  private static final Logger LOG = Logger.getInstance(BuildFilePanel.class);

  protected final Project myProject;
  @Nullable protected final GradleBuildFile myGradleBuildFile;
  protected boolean myModified = false;
  private final JPanel myPanel;
  @Nullable protected final Module myModule;

  public BuildFilePanel(@NotNull Project project, @NotNull String moduleGradlePath) {
    super(new BorderLayout());
    myPanel = new JPanel();
    JBScrollPane scrollPane = new JBScrollPane(myPanel);
    add(scrollPane, BorderLayout.CENTER);

    myProject = project;

    myModule = GradleUtil.findModuleByGradlePath(myProject, moduleGradlePath);
    myGradleBuildFile = myModule != null ? GradleBuildFile.get(myModule) : null;
    if (myGradleBuildFile == null) {
      LOG.info("Unable to find Gradle build file for module " + moduleGradlePath);
    }
  }

  public void init() {
    addItems(myPanel);
  }

  protected abstract void addItems(@NotNull JPanel parent);

  @Override
  public boolean isModified() {
    return myModified;
  }
}
