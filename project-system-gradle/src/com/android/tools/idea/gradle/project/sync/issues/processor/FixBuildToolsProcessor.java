/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_VERISON_REMOVED;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_VERSION_CHANGED;

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class FixBuildToolsProcessor extends BaseRefactoringProcessor {
  @NotNull private final List<VirtualFile> myBuildFiles;
  @NotNull private final String myVersion;
  private final boolean myRequestSync;
  private final boolean myRemoveBuildTools;

  public FixBuildToolsProcessor(@NotNull Project project,
                                @NotNull List<VirtualFile> buildFiles,
                                @NotNull String version,
                                boolean requestSync,
                                boolean removeBuildTools) {
    super(project);
    myBuildFiles = buildFiles;
    myVersion = version;
    myRequestSync = requestSync;
    myRemoveBuildTools = removeBuildTools;
  }

  @NotNull
  @Override
  public UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return "Values to " + (myRemoveBuildTools ? "remove " : "update ") + UsageViewBundle.getReferencesString(usagesCount, filesCount);
      }

      @NotNull
      @Override
      public PsiElement[] getElements() {
        return PsiElement.EMPTY_ARRAY;
      }

      @Override
      public String getProcessedElementsHeader() {
        return (myRemoveBuildTools ? "Remove" : "Update") + " Android Build Tools Versions";
      }
    };
  }

  @NotNull
  @Override
  public UsageInfo[] findUsages() {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);

    List<UsageInfo> usages = new ArrayList<>();
    for (VirtualFile file : myBuildFiles) {
      if (!file.isValid() || !file.isWritable()) {
        continue;
      }
      AndroidModel android = projectBuildModel.getModuleBuildModel(file).android();
      ResolvedPropertyModel buildToolsVersion = android.buildToolsVersion();
      if (myVersion.equals(buildToolsVersion.toString())) {
        continue;
      }

      PsiElement element = buildToolsVersion.getFullExpressionPsiElement();
      if (element != null) {
        usages.add(new UsageInfo(element));
      }
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  public void performRefactoring(@NotNull UsageInfo[] usages) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);

    List<PsiElement> elements = Arrays.stream(usages).map(usage -> usage.getElement()).collect(Collectors.toList());
    for (VirtualFile file : myBuildFiles) {
      AndroidModel android = projectBuildModel.getModuleBuildModel(file).android();
      ResolvedPropertyModel buildToolsVersion = android.buildToolsVersion();
      PsiElement element = buildToolsVersion.getFullExpressionPsiElement();
      if (element != null && elements.contains(element)) {
        if (myRemoveBuildTools) {
          buildToolsVersion.delete();
        }
        else {
          buildToolsVersion.setValue(myVersion);
        }
      }
    }

    projectBuildModel.applyChanges();

    if (myRequestSync) {
      GradleSyncStats.Trigger trigger = myRemoveBuildTools ? TRIGGER_QF_BUILD_TOOLS_VERISON_REMOVED : TRIGGER_QF_BUILD_TOOLS_VERSION_CHANGED;
      GradleSyncInvoker.getInstance().requestProjectSync(myProject, new GradleSyncInvoker.Request(trigger), null);
    }
  }

  @NotNull
  @Override
  public String getCommandName() {
    return (myRemoveBuildTools ? "Remove" : "Update") + " Android Build Tools Version";
  }
}