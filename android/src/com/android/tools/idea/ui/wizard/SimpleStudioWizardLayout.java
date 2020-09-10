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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * The general look and feel for all Studio-specific wizards.
 */
public final class SimpleStudioWizardLayout implements ModelWizardDialog.CustomLayout {
  private static final Dimension DEFAULT_MIN_SIZE = JBUI.size(600, 350);
  private static final Dimension DEFAULT_PREFERRED_SIZE = JBUI.size(900, 650);

  @NotNull
  @Override
  public JPanel decorate(@NotNull ModelWizard.TitleHeader titleHeader, @NotNull JPanel innerPanel) {
    return innerPanel;
  }

  @Override
  public Dimension getDefaultPreferredSize() {
    return DEFAULT_PREFERRED_SIZE;
  }

  @Override
  public Dimension getDefaultMinSize() {
    return DEFAULT_MIN_SIZE;
  }

  @Override
  public void dispose() {
  }
}
