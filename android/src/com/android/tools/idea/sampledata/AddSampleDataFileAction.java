/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sampledata;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.Features;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Action that displays the "Add Sample Data" dialog
 */
public class AddSampleDataFileAction extends AnAction {

  public AddSampleDataFileAction() {
    super("Add sample data file");
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    if (!Features.NELE_MOCK_DATA || project == null) {
      e.getPresentation().setVisible(false);
      return;
    }
    Module[] modules = Projects.getModulesToBuildFromSelection(project, e.getDataContext());
    e.getPresentation().setVisible(modules.length == 1 && AndroidFacet.getInstance(modules[0]) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null; // update would disable the action if project == null
    Module[] modules = Projects.getModulesToBuildFromSelection(project, e.getDataContext());
    AndroidFacet facet = AndroidFacet.getInstance(modules[0]);
    if (facet == null) {
      return;
    }

    new AddSampleDataDialog(facet).showAndGet();
  }
}
