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

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;

/**
 *
 */
public class DummyWizardForTesting extends AnAction {
  @VisibleForTesting
  public static void showDummyUIForTestingPurposesOnly() {
    NewProjectWizardDynamic wizard = new NewProjectWizardDynamic(null, null);
    wizard.init();
    wizard.show();
  }

  public DummyWizardForTesting() {
    super("Test New Wizard");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showDummyUIForTestingPurposesOnly();
  }

  public static class ClearPrefsAction extends AnAction {

    public ClearPrefsAction() {
      super("Clear Saved Values...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      PropertiesComponent properties = PropertiesComponent.getInstance();
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey(FormFactorUtils.PHONE_TABLET_FORM_FACTOR_NAME));
      properties.unsetValue(FormFactorUtils.getPropertiesComponentMinSdkKey("Glass"));
      properties.unsetValue(ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN);
    }
  }
}

