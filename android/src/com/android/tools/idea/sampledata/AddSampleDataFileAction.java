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

//import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action that displays the "Add Sample Data" dialog
 */
public class AddSampleDataFileAction extends AnAction {

  public AddSampleDataFileAction() {
    super("Add sample data file");
  }

  @Nullable
  private static AndroidFacet getFacetFromAction(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return null;
    }
    return null;/*
    return Arrays.stream(GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(e.getDataContext()))
      .map(AndroidFacet::getInstance)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);*/
  }

  @Override
  public void update(@NotNull AnActionEvent e) {

    e.getPresentation().setEnabledAndVisible(getFacetFromAction(e) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    AndroidFacet facet = getFacetFromAction(e);
    if (facet == null) {
      return;
    }

    new AddSampleDataDialog(facet).showAndGet();
  }
}
