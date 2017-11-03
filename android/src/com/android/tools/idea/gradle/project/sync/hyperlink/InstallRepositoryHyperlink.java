/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.ide.common.repository.SdkMavenRepository;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

public class InstallRepositoryHyperlink extends NotificationHyperlink {
  @NotNull private final SdkMavenRepository myRepository;
  @NotNull private final String myDependency;

  public InstallRepositoryHyperlink(@NotNull SdkMavenRepository repository, @NotNull String dependency) {
    super("install.m2.repo", "Install Repository and sync project");
    myRepository = repository;
    myDependency = dependency;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = Lists.newArrayList();
    requested.add(myRepository.getPackageId());
    String noOpMessage = String.format("Could not find dependency \"%1$s\"", myDependency);
    ModelWizardDialog dialog = createDialogForPaths(project, requested, noOpMessage);
    if (dialog != null) {
      dialog.setTitle("Install Missing Components");
      if (dialog.showAndGet()) {
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED, null);
      }
    }
  }

  @VisibleForTesting
  @NotNull
  public SdkMavenRepository getRepository() {
    return myRepository;
  }

  @VisibleForTesting
  @NotNull
  public String getDependency() {
    return myDependency;
  }
}
