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
package com.android.tools.idea.wizard;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Wizard for creating a new Android activity.
 */
public final class NewAndroidActivityWizard extends DynamicWizard {
  private final AddAndroidActivityPath myAddActivityPath;

  public NewAndroidActivityWizard(@NotNull Module module) {
    super(module.getProject(), module, "New Android Activity");
    myAddActivityPath = new AddAndroidActivityPath(null, ImmutableMap.<String, Object>of(), getDisposable());
  }

  @Override
  protected void init() {
    addPath(myAddActivityPath);
    super.init();
    getContentPanel().setPreferredSize(new Dimension(800, 640));
  }

  @Override
  public void performFinishingActions() {
    // Do nothing
  }
}
