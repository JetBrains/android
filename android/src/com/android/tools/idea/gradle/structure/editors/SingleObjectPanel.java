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

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SingleObjectPanel extends BuildFilePanel implements KeyValuePane.ModificationListener {
  protected final GrClosableBlock myRoot;
  protected final Map<BuildFileKey, Object> myValues = Maps.newHashMap();
  protected final List<BuildFileKey> myProperties;
  protected final KeyValuePane myDetailPane;
  private Set<BuildFileKey> myModifiedKeys = Sets.newHashSet();

  public SingleObjectPanel(@NotNull Project project, @NotNull String moduleName, @Nullable GrClosableBlock root,
                           @NotNull List<BuildFileKey> properties) {
    super(project, moduleName);
    myRoot = root;
    myProperties = properties;
    myDetailPane = new KeyValuePane(project, this);
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
      if (!myModifiedKeys.contains(key)) {
        continue;
      }
      Object value = myValues.get(key);
      if (value != null) {
        myGradleBuildFile.setValue(myRoot, key, value);
      } else {
        myGradleBuildFile.removeValue(myRoot, key);
      }
    }
    myModifiedKeys.clear();
  }

  @Override
  public boolean isModified() {
    return !myModifiedKeys.isEmpty();
  }

  @Override
  public void modified(@NotNull BuildFileKey key) {
    myModifiedKeys.add(key);
  }
}
