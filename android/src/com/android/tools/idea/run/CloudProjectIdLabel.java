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
package com.android.tools.idea.run;

import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.AndroidRunConfigurationBase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CloudProjectIdLabel extends JBLabel {
  private static final String CLOUD_PROJECT_PROMPT = "Please select a project...";

  private Module myCurrentModule;

  // a cache of configuration selected by module, so that if the module selection changes back and forth, we retain
  // the appropriate selected project id
  private static Map<Module, String> myProjectByModuleCache = Maps.newHashMapWithExpectedSize(5);

  public CloudProjectIdLabel() {
    updateCloudProjectId(CLOUD_PROJECT_PROMPT);
  }

  public boolean isProjectSpecified() {
    return !getText().isEmpty() && !getText().equals(CLOUD_PROJECT_PROMPT);
  }

  public void updateCloudProjectId(@NotNull String cloudProjectId) {
    if (cloudProjectId.isEmpty() || cloudProjectId.equals(CLOUD_PROJECT_PROMPT)) {
      Font currentFont = getFont();
      setFont(new Font("Dialog", Font.BOLD, currentFont.getSize()));
      setForeground(JBColor.RED);
      setText(CLOUD_PROJECT_PROMPT);
    } else {
      Font currentFont = getFont();
      setFont(new Font("Dialog", Font.PLAIN, currentFont.getSize()));
      setForeground(JBColor.BLACK);
      setText(cloudProjectId);
    }
    rememberChosenProjectId();
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myCurrentModule = facet.getModule();
    restoreChosenProjectId();
  }

  private void rememberChosenProjectId() {
    if (myCurrentModule == null) {
      return;
    }

    myProjectByModuleCache.put(myCurrentModule, getText());
  }

  private void restoreChosenProjectId() {
    if (myCurrentModule == null) {
      return;
    }

    String projectId = myProjectByModuleCache.get(myCurrentModule);
    if (projectId != null) {
      updateCloudProjectId(projectId);
    }
  }

}
