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
package com.android.tools.idea.wizard.model.demo.npw.steps;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.demo.npw.models.ProjectModel;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions.not;

public final class ConfigureProjectStep extends ModelWizardStep<ProjectModel> {
  private final TextProperty myProjectLocationText;
  private final TextProperty myAppNameText;
  private final TextProperty myPackageText;
  private final TextProperty myDomaninText;
  private JTextField myAppNameTextField;
  private JTextField myDomainTextfield;
  private JBLabel myPackageLabel;
  private JBLabel myProjectLocationLabel;
  private JPanel myRootPanel;
  private BindingsManager myBindings = new BindingsManager();

  public ConfigureProjectStep(@NotNull ProjectModel model) {
    super(model, "Configure Project");

    myProjectLocationText = new TextProperty(myProjectLocationLabel);
    myAppNameText = new TextProperty(myAppNameTextField);
    myPackageText = new TextProperty(myPackageLabel);
    myDomaninText = new TextProperty(myDomainTextfield);

    myBindings.bind(myProjectLocationText, new StringExpression(myAppNameText) {
      @NotNull
      @Override
      public String get() {
        return String.format("/usr/local/demopath/code/%s", CharMatcher.WHITESPACE.removeFrom(myAppNameText.get()));
      }
    });

    myBindings.bind(myPackageText, new StringExpression(myDomaninText, myAppNameText) {
      @NotNull
      @Override
      public String get() {
        List<String> parts = Splitter.on('.').omitEmptyStrings().splitToList(myDomaninText.get());
        parts = Lists.newArrayList(Lists.reverse(parts));
        if (!myAppNameText.get().isEmpty()) {
          parts.add(CharMatcher.WHITESPACE.removeFrom(myAppNameText.get()).toLowerCase());
        }
        return Joiner.on('.').join(parts);
      }
    });

    myAppNameTextField.setText("Application");
    myDomainTextfield.setText("example.com");
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppNameTextField;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return not(myAppNameText.isEmpty());
  }

  @Override
  protected void onProceeding() {
    getModel().setApplicationName(myAppNameText.get());
    getModel().setPackageName(myPackageText.get());
    getModel().setProjectLocation(myProjectLocationText.get());
  }
}
