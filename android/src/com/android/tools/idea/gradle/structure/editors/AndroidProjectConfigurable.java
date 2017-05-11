/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ActionRunner;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

/**
 * A standard {@linkplan Configurable} instance that allows editing of project-wide project structure parameters, including the top-level
 * build file.
 */
public class AndroidProjectConfigurable extends NamedConfigurable implements KeyValuePane.ModificationListener {
  private static final Logger LOG = Logger.getInstance(AndroidProjectConfigurable.class);
  private static final String DISPLAY_NAME = "Project";
  private final KeyValuePane myKeyValuePane;
  private final Project myProject;
  private final GradleBuildFile myGradleBuildFile;
  private final Map<BuildFileKey, Object> myProjectProperties = Maps.newHashMap();
  private Set<BuildFileKey> myModifiedKeys = Sets.newHashSet();

  public static final ImmutableList<BuildFileKey> PROJECT_PROPERTIES =
    ImmutableList.of(BuildFileKey.GRADLE_WRAPPER_VERSION, BuildFileKey.PLUGIN_VERSION, BuildFileKey.PLUGIN_REPOSITORY,
                     BuildFileKey.ALLPROJECTS_LIBRARY_REPOSITORY);

  public AndroidProjectConfigurable(Project project) {
    if (project.isDefault()) {
      throw new IllegalArgumentException("Can't instantiate an AndroidProjectConfigurable with the default project.");
    }
    myKeyValuePane = new KeyValuePane(project, this);
    myProject = project;
    VirtualFile vf = project.getBaseDir().findChild(SdkConstants.FN_BUILD_GRADLE);
    if (vf != null) {
      myGradleBuildFile = new GradleBuildFile(vf, project);
    } else {
      myGradleBuildFile = null;
    }
  }

  @Override
  public void setDisplayName(String name) {
  }

  @Override
  public Object getEditableObject() {
    return myProject;
  }

  @Override
  public String getBannerSlogan() {
    return DISPLAY_NAME;
  }

  @Override
  public JComponent createOptionsPanel() {
    myKeyValuePane.init(myGradleBuildFile, PROJECT_PROPERTIES);
    return myKeyValuePane;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public boolean isModified() {
    return !myModifiedKeys.isEmpty();
  }

  @Override
  public void modified(@NotNull BuildFileKey key) {
    myModifiedKeys.add(key);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myGradleBuildFile == null) {
      return;
    }
    VirtualFile file = myGradleBuildFile.getFile();
    if (!ReadonlyStatusHandler.ensureFilesWritable(myProject, file)) {
      throw new ConfigurationException(String.format("Build file %1$s is not writable", file.getPath()));
    }

    CommandProcessor.getInstance().runUndoTransparentAction(() -> {
      try {
        ActionRunner.runInsideWriteAction(() -> {
          for (BuildFileKey key : PROJECT_PROPERTIES) {
            if (key == BuildFileKey.GRADLE_WRAPPER_VERSION || !myModifiedKeys.contains(key)) {
              continue;
            }
            Object value = myProjectProperties.get(key);
            if (value != null) {
              myGradleBuildFile.setValue(key, value);
            } else {
              myGradleBuildFile.removeValue(null, key);
            }
          }
          Object wrapperVersion = myProjectProperties.get(BuildFileKey.GRADLE_WRAPPER_VERSION);
          GradleWrapper gradleWrapper = GradleWrapper.find(myProject);
          if (wrapperVersion != null && gradleWrapper != null) {
            boolean updated = gradleWrapper.updateDistributionUrlAndDisplayFailure(wrapperVersion.toString());
            if (updated) {
              VirtualFile virtualFile = gradleWrapper.getPropertiesFile();
              if (virtualFile != null) {
                virtualFile.refresh(false, false);
              }
            }
          }
          myModifiedKeys.clear();
        });
      }
      catch (Exception e) {
        LOG.error("Error while applying changes", e);
      }
    });
  }

  @Override
  public void reset() {
    myProjectProperties.clear();
    if (myGradleBuildFile == null) {
      return;
    }

    for (BuildFileKey key : PROJECT_PROPERTIES) {
      Object value = myGradleBuildFile.getValue(key);
      if (value != null) {
        myProjectProperties.put(key, value);
      }
    }
    try {
      GradleWrapper gradleWrapper = GradleWrapper.find(myProject);
      if (gradleWrapper != null) {
        String wrapperVersion = gradleWrapper.getGradleVersion();
        myProjectProperties.put(BuildFileKey.GRADLE_WRAPPER_VERSION, wrapperVersion);
      }
    } catch (Exception e) {
      LOG.warn("Error while saving Gradle wrapper properties", e);
    }
    myKeyValuePane.setCurrentBuildFileObject(myProjectProperties);
    myKeyValuePane.updateUiFromCurrentObject();
  }

  @Nullable
  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Project;
  }
}
