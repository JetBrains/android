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
package com.android.tools.idea.gradle.actions;

import com.android.builder.model.AndroidArtifactOutput;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class BuildApkAction extends DumbAwareAction {
  private static final String ACTION_TEXT = "Build APK(s)";

  public BuildApkAction() {
    super(ACTION_TEXT);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && GradleProjectInfo.getInstance(project).isBuildWithGradle());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      List<Module> appModules = new ArrayList<>();
      Map<Module, File> appModulesToOutputs = new HashMap<>();

      for (Module module : ModuleManager.getInstance(project).getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
          if (androidModel != null && (androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_APP
                                       || androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP)) {
            String assembleTaskName = facet.getProperties().ASSEMBLE_TASK_NAME;
            if (isNotEmpty(assembleTaskName)) {
              appModules.add(module);
              //if there's just one APK, we'll pass it directly to the GoToApkLocationTask,
              //if more than one APK, pass the output folder instead
              File outputFolderOrApk = null;
              for (AndroidArtifactOutput output : androidModel.getMainArtifact().getOutputs()) {
                if (output.getOutputs().size() == 1 && outputFolderOrApk == null){
                  outputFolderOrApk = output.getMainOutputFile().getOutputFile();
                } else {
                  outputFolderOrApk = output.getMainOutputFile().getOutputFile().getParentFile();
                  break;
                }
              }
              appModulesToOutputs.put(module, outputFolderOrApk);
            }
          }
        }
      }

      if (!appModules.isEmpty()) {
        GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);
        gradleBuildInvoker.add(new GoToApkLocationTask(appModulesToOutputs, ACTION_TEXT));
        gradleBuildInvoker.assemble(appModules.toArray(new Module[appModules.size()]), TestCompileType.ALL);
      }
    }
  }
}
