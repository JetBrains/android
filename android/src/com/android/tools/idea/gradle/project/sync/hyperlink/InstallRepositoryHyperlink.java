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
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InstallRepositoryHyperlink extends NotificationHyperlink {
  @NotNull private final SdkMavenRepository myRepository;

  public InstallRepositoryHyperlink(@NotNull SdkMavenRepository repository) {
    super("install.m2.repo", "Install Repository and sync project");
    myRepository = repository;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = Lists.newArrayList();
    requested.add(myRepository.getPackageId());
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
    if (dialog != null) {
      dialog.setTitle("Install Missing Components");
      if (dialog.showAndGet()) {
        GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, null);
      }
    }
  }

  @VisibleForTesting
  @NotNull
  public SdkMavenRepository getRepository() {
    return myRepository;
  }
}
