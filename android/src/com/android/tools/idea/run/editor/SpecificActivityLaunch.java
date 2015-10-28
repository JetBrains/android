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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.AndroidApplicationLauncher;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.activity.AndroidActivityLauncher;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class SpecificActivityLaunch extends LaunchOption<SpecificActivityLaunch.State> {
  public static final SpecificActivityLaunch INSTANCE = new SpecificActivityLaunch();

  public static final class State extends LaunchOptionState {
    public String ACTIVITY_CLASS = "";

    @Override
    public AndroidApplicationLauncher getLauncher(@NotNull AndroidFacet facet, @NotNull String extraAmOptions) {
      return new AndroidActivityLauncher(getActivityLocator(facet), extraAmOptions);
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      try {
        getActivityLocator(facet).validate();
        return ImmutableList.of();
      }
      catch (ActivityLocator.ActivityLocatorException e) {
        // The launch will probably fail, but we allow the user to continue in case we are looking at stale data.
        return ImmutableList.of(ValidationError.warning(e.getMessage()));
      }
    }

    @NotNull
    private SpecificActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
      return new SpecificActivityLocator(facet, ACTIVITY_CLASS);
    }
  }

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Specified Activity";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new SpecificActivityConfigurable(project, context);
  }
}
