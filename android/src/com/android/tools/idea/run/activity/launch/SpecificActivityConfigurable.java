/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch;

import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.util.TreeClassChooser;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidTreeClassChooserFactory;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpecificActivityConfigurable implements LaunchOptionConfigurable<SpecificActivityLaunch.State> {
  private final Project myProject;
  private final LaunchOptionConfigurableContext myContext;

  private JPanel myPanel;
  private ComponentWithBrowseButton<EditorTextField> myActivityField;
  private JCheckBox mySkipActivityValidationCheckBox;

  public SpecificActivityConfigurable(@NotNull final Project project, @NotNull final LaunchOptionConfigurableContext context) {
    myProject = project;
    myContext = context;

    // Perform Form creation once the project and context are saved into fields
    $$$setupUI$$$();

    myActivityField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!project.isInitialized()) {
          return;
        }
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass activityBaseClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
        if (activityBaseClass == null) {
          Messages.showErrorDialog(project, AndroidBundle.message("cant.find.activity.class.error"), "Specific Activity Launcher");
          return;
        }
        Module module = context.getModule();
        if (module == null) {
          Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), "Specific Activity Launcher");
          return;
        }
        PsiClass initialSelection =
          facade.findClass(myActivityField.getChildComponent().getText(), module.getModuleWithDependenciesScope());
        TreeClassChooser chooser = AndroidTreeClassChooserFactory.INSTANCE.createInheritanceClassChooser(
          project, "Select Activity Class", module.getModuleWithDependenciesScope(), activityBaseClass, initialSelection, null
        );
        chooser.showDialog();
        PsiClass selClass = chooser.getSelected();
        if (selClass != null) {
          myActivityField.getChildComponent().setText(ActivityLocatorUtils.getQualifiedActivityName(selClass));
        }
      }
    });
  }

  private void $$$setupUI$$$() {
  }

  private void createUIComponents() {
    final EditorTextField editorTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, myProject, "") {
      @Override
      protected @NotNull EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

        if (file != null) {
          DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(file, false);
        }
        editor.putUserData(LaunchOptionConfigurableContext.KEY, myContext);
        return editor;
      }
    };

    myActivityField = new ComponentWithBrowseButton<EditorTextField>(editorTextField, null);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void resetFrom(@NotNull SpecificActivityLaunch.State state) {
    myActivityField.getChildComponent().setText(StringUtil.notNullize(state.ACTIVITY_CLASS));
    mySkipActivityValidationCheckBox.setSelected(state.SKIP_ACTIVITY_VALIDATION);
  }

  @Override
  public void applyTo(@NotNull SpecificActivityLaunch.State state) {
    state.ACTIVITY_CLASS = StringUtil.notNullize(myActivityField.getChildComponent().getText());
    state.SKIP_ACTIVITY_VALIDATION = mySkipActivityValidationCheckBox.isSelected();
  }
}
