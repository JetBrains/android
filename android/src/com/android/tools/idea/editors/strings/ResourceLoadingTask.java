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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

final class ResourceLoadingTask extends Task.Backgroundable {
  private final StringResourceViewPanel myPanel;
  private final Supplier<LocalResourceRepository> myRepositorySupplier;

  private LocalResourceRepository myRepository;

  ResourceLoadingTask(@NotNull StringResourceViewPanel panel) {
    this(panel, () -> ModuleResourceRepository.getOrCreateInstance(panel.getFacet()));
  }

  ResourceLoadingTask(@NotNull StringResourceViewPanel panel, @NotNull Supplier<LocalResourceRepository> repositorySupplier) {
    super(panel.getFacet().getModule().getProject(), "Loading String Resources...");

    myPanel = panel;
    myRepositorySupplier = repositorySupplier;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    myRepository = myRepositorySupplier.get();
  }

  @Override
  public void onSuccess() {
    myPanel.getTable().setModel(new StringResourceTableModel(StringResourceRepository.create(myRepository), myPanel.getFacet()));
    myPanel.getLoadingPanel().stopLoading();
  }

  @Override
  public void onCancel() {
    myPanel.getLoadingPanel().stopLoading();
  }
}
