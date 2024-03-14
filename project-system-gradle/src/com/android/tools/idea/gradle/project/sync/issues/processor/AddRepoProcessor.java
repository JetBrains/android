/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_REPOSITORY_ADDED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class AddRepoProcessor extends BaseRefactoringProcessor {
  public enum Repository {
    GOOGLE("Google");

    @NotNull
    private final String myName;

    Repository(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getDisplayName() {
      return myName;
    }
  }

  @NotNull private final List<VirtualFile> myBuildFiles;
  @NotNull private final Repository myRepository;
  private final boolean myRequestSync;

  public AddRepoProcessor(@NotNull Project project,
                          @NotNull List<VirtualFile> buildFiles,
                          @NotNull Repository repository,
                          boolean requestSync) {
    super(project);
    myBuildFiles = buildFiles;
    myRequestSync = requestSync;
    myRepository = repository;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return String.format(Locale.US, "File%s to add %s repository to (%d file%s found)", (filesCount == 1 ? "" : "s"),
                             myRepository.getDisplayName(), filesCount, (filesCount == 1 ? "" : "s"));
      }

      @NotNull
      @Override
      public PsiElement[] getElements() {
        return PsiElement.EMPTY_ARRAY;
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Add " + myRepository.getDisplayName() + " repository";
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    List<UsageInfo> usages = new ArrayList<>();

    GradleSettingsModel settingsModel = projectBuildModel.getProjectSettingsModel();
    if (settingsModel != null) {
      PsiElement psiElement = settingsModel.dependencyResolutionManagement().repositories().getPsiElement();
      if (psiElement != null) {
        usages.add(new UsageInfo(psiElement));
        return usages.toArray(UsageInfo.EMPTY_ARRAY);
      }
    }

    for (VirtualFile file : myBuildFiles) {
      if (!file.isValid() || !file.isWritable()) {
        continue;
      }
      GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(file);
      PsiElement psiElement = buildModel.getPsiElement();
      // Make sure its a PsiFile to get the correct preview
      if (psiElement != null) {
        usages.add(new UsageInfo(psiElement));
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);

    List<PsiElement> elements = Arrays.stream(usages).map(UsageInfo::getElement).toList();
    GradleSettingsModel settingsModel = projectBuildModel.getProjectSettingsModel();
    if (settingsModel != null) {
      PsiElement psiElement = settingsModel.dependencyResolutionManagement().repositories().getPsiElement();
      if (psiElement != null && elements.contains(psiElement) && myRepository.equals(Repository.GOOGLE)) {
        settingsModel.dependencyResolutionManagement().repositories().addGoogleMavenRepository();
        projectBuildModel.applyChanges();
        if (myRequestSync) {
          GradleSyncInvoker.getInstance().requestProjectSync(myProject, new GradleSyncInvoker.Request(TRIGGER_QF_REPOSITORY_ADDED), null);
        }
        return;
      }
    }
    for (VirtualFile file : myBuildFiles) {
      GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(file);
      PsiElement filePsiElement = buildModel.getPsiElement();
      if (filePsiElement != null && elements.contains(filePsiElement)) {
        switch (myRepository) {
          case GOOGLE:
            buildModel.repositories().addGoogleMavenRepository();
            PsiElement buildScriptElement = buildModel.buildscript().getPsiElement();
            if (buildScriptElement != null) {
              buildModel.buildscript().repositories().addGoogleMavenRepository();
            }
            break;
          default:
            throw new IllegalStateException("No handle for requested repository: " + myRepository.name());
        }
      }
    }

    projectBuildModel.applyChanges();

    if (myRequestSync) {
      GradleSyncInvoker.getInstance().requestProjectSync(myProject, new GradleSyncInvoker.Request(TRIGGER_QF_REPOSITORY_ADDED), null);
    }
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return "Add " + myRepository.getDisplayName() + " repository";
  }
}
