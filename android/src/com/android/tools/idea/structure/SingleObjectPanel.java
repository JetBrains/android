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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class SingleObjectPanel extends BuildFilePanel {
  protected final GrClosableBlock myRoot;
  protected final Map<BuildFileKey, Object> myValues = Maps.newHashMap();
  protected final List<BuildFileKey> myProperties;
  protected final KeyValuePane myDetailPane;

  public SingleObjectPanel(@NotNull Project project, @NotNull String moduleName, @Nullable GrClosableBlock root,
                           @NotNull List<BuildFileKey> properties) {
    super(project, moduleName);
    myRoot = root;
    myProperties = properties;
    myDetailPane = new KeyValuePane(project);
    if (myGradleBuildFile != null) {
      for (BuildFileKey key : properties) {
        Object value = myGradleBuildFile.getValue(myRoot, key);
        if (value != null) {
          myValues.put(key, value);
        }
      }
    }
  }

  @Override
  protected void addItems(@NotNull JPanel parent) {
    if (myGradleBuildFile == null) {
      return;
    }
    myDetailPane.init(myGradleBuildFile, myProperties);
    myDetailPane.setCurrentBuildFileObject(myValues);
    myDetailPane.updateUiFromCurrentObject();
    myDetailPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    add(myDetailPane, BorderLayout.NORTH);
  }

  @Override
  public void apply() {
    if (myGradleBuildFile == null) {
      return;
    }
    for (BuildFileKey key : myProperties) {
      Object value = myValues.get(key);
      if (value != null) {
        myGradleBuildFile.setValue(myRoot, key, value);
      } else {
        myGradleBuildFile.removeValue(myRoot, key);
      }
    }
    myDetailPane.clearModified();
  }

  @Override
  public boolean isModified() {
    return myDetailPane.isModified();
  }
}
