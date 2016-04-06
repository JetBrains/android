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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Wizard for creating a new Android activity.
 *
 * @deprecated Replaced by {@link ConfigureTemplateParametersStep}
 */
public final class NewAndroidActivityWizard extends DynamicWizard {
  @Nullable private final VirtualFile myTargetFile;
  @Nullable private final File myTemplate;
  private AddAndroidActivityPath myPath;

  public NewAndroidActivityWizard(Module module, @Nullable VirtualFile targetFile, @Nullable File template) {
    super(module.getProject(), module, "New Android Activity");
    myTargetFile = targetFile;
    myTemplate = template;
  }

  @Override
  public void init() {
    myPath = new AddAndroidActivityPath(myTargetFile, myTemplate, ImmutableMap.<String, Object>of(), getDisposable());
    addPath(myPath);
    super.init();
    myHost.setPreferredWindowSize(JBUI.size(800, 640));
  }

  public void setOpenCreatedFiles(boolean openCreatedFiles) {
    getState().put(AddAndroidActivityPath.KEY_OPEN_EDITORS, openCreatedFiles);
  }

  @Override
  protected String getWizardActionDescription() {
    return myPath.getActionDescription();
  }

  @Override
  public void performFinishingActions() {
    // Do nothing
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Creating activity...";
  }
}
