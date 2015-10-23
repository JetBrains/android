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

import com.android.tools.idea.run.*;
import com.android.tools.idea.run.activity.ActivityLocator;
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

public class DeepLinkLaunch extends LaunchOption<DeepLinkLaunch.State> {
  public static final DeepLinkLaunch INSTANCE = new DeepLinkLaunch();

  public static final class State extends LaunchOptionState {
    public String DEEP_LINK = "";

    @Override
    public AndroidApplicationLauncher getLauncher(@NotNull AndroidFacet facet, @NotNull String extraAmOptions) {
      return new AndroidDeepLinkLauncher(DEEP_LINK, extraAmOptions);
    }

    @NotNull
    @Override
    public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
      if  (DEEP_LINK == null || DEEP_LINK.isEmpty()) {
        return ImmutableList.of(ValidationError.warning("Deep link not specified"));
      } else {
        return ImmutableList.of();
      }
    }
  }

  @NotNull
  @Override
  public String getId() {
    return AndroidRunConfiguration.LAUNCH_DEEP_LINK;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Deep link";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @NotNull
  @Override
  public LaunchOptionConfigurable<State> createConfigurable(@NotNull Project project, @NotNull LaunchOptionConfigurableContext context) {
    return new DeepLinkConfigurable(project, context);
  }

  private static class DeepLinkConfigurable implements LaunchOptionConfigurable<State> {
    private final ComponentWithBrowseButton<EditorTextField> myDeepLinkField;

    public DeepLinkConfigurable(@NotNull final Project project, @NotNull final LaunchOptionConfigurableContext context) {
      myDeepLinkField = new ComponentWithBrowseButton<EditorTextField>(new EditorTextField(), null);

      myDeepLinkField.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (!project.isInitialized()) {
            return;
          }
          Module module = context.getModule();
          if (module == null) {
            Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), "Deep Link Launcher");
            return;
          }
          DeepLinkChooserDialog dialog = new DeepLinkChooserDialog(project, module);
          dialog.setTitle("Select Deep Link");
          dialog.show();

          String deepLinkSelected = dialog.getSelectedDeepLink();
          if (deepLinkSelected != null && !deepLinkSelected.isEmpty()) {
            myDeepLinkField.getChildComponent().setText(deepLinkSelected);
          }
        }
      });
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return myDeepLinkField;
    }

    @Override
    public void resetFrom(@NotNull State state) {
      myDeepLinkField.getChildComponent().setText(StringUtil.notNullize(state.DEEP_LINK));
    }

    @Override
    public void applyTo(@NotNull State state) {
      state.DEEP_LINK = StringUtil.notNullize(myDeepLinkField.getChildComponent().getText());
    }
  }
}

