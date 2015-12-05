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

package com.android.tools.idea.npw.template;

import com.android.tools.idea.npw.assetstudio.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.GenerateIconPanel;
import com.android.tools.idea.templates.StringEvaluator;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Strings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Step for supporting a template.xml's {@code <icon>} tag if one exists (which tells the template
 * to also generate icons in addition to regular files).
 */
public final class GenerateIconStep extends ModelWizardStep<RenderTemplateModel> {

  private final StudioWizardStepPanel myStudioPanel;
  private final GenerateIconPanel myGenerateIconPanel;

  private final ListenerManager myListeners = new ListenerManager();

  public GenerateIconStep(@NotNull RenderTemplateModel model) {
    super(model, "Generate Icons");

    AndroidIconType iconType = getModel().getTemplateHandle().getMetadata().getIconType();
    assert iconType != null; // It's an error to create <icon> tags w/o types
    myGenerateIconPanel = new GenerateIconPanel(this, getModel().getFacet(), iconType);

    myListeners.listenAndFire(model.getSourceSet(), new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myGenerateIconPanel.setProjectPaths(getModel().getPaths());
      }
    });

    myStudioPanel = new StudioWizardStepPanel(myGenerateIconPanel, "Convert a source asset into " + iconType.getDisplayName());
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  protected void onEntering() {
    TemplateHandle templateHandle = getModel().getTemplateHandle();
    String iconNameExpression = templateHandle.getMetadata().getIconName();
    String iconName = null;
    if (iconNameExpression != null && !iconNameExpression.isEmpty()) {
      StringEvaluator evaluator = new StringEvaluator();
      iconName = evaluator.evaluate(iconNameExpression, getModel().getTemplateValues());
    }

    myGenerateIconPanel.setOutputName(Strings.nullToEmpty(iconName));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myGenerateIconPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myGenerateIconPanel.createIconGenerator());
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }

}
