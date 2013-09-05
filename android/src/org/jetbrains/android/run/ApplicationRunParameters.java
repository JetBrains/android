/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.run;

import com.android.annotations.Nullable;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 4:15:59 PM
 * To change this template use File | Settings | File Templates.
 */
class ApplicationRunParameters implements ConfigurationSpecificEditor<AndroidRunConfiguration> {
  public static final Key<ApplicationRunParameters> ACTIVITY_CLASS_TEXT_FIELD_KEY = Key.create("ActivityClassTextField");

  private ComponentWithBrowseButton<EditorTextField> myActivityField;
  private JRadioButton myLaunchDefaultButton;
  private JRadioButton myLaunchCustomButton;
  private JPanel myPanel;
  private JRadioButton myDoNothingButton;
  private JCheckBox myDeployAndInstallCheckBox;
  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;

  ApplicationRunParameters(final Project project, final ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myActivityField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!project.isInitialized()) {
          return;
        }
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass activityBaseClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
        if (activityBaseClass == null) {
          Messages.showErrorDialog(myPanel, AndroidBundle.message("cant.find.activity.class.error"));
          return;
        }
        Module module = moduleSelector.getModule();
        if (module == null) {
          Messages.showErrorDialog(myPanel, ExecutionBundle.message("module.not.specified.error.text"));
          return;
        }
        PsiClass initialSelection = facade.findClass(myActivityField.getChildComponent().getText(), module.getModuleWithDependenciesScope());
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createInheritanceClassChooser("Select Activity Class", module.getModuleWithDependenciesScope(), activityBaseClass,
                                         initialSelection, null);
        chooser.showDialog();
        PsiClass selClass = chooser.getSelected();
        if (selClass != null) {
          myActivityField.getChildComponent().setText(selClass.getQualifiedName());
        }
      }
    });
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myActivityField.setEnabled(myLaunchCustomButton.isSelected());
      }
    };
    myLaunchCustomButton.addActionListener(listener);
    myLaunchDefaultButton.addActionListener(listener);
    myDoNothingButton.addActionListener(listener);
  }

  @Nullable
  public Module getModule() {
    return myModuleSelector.getModule();
  }

  @Override
  public void resetFrom(AndroidRunConfiguration configuration) {
    boolean launchSpecificActivity = configuration.MODE.equals(AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY);
    if (configuration.MODE.equals(AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY)) {
      myLaunchDefaultButton.setSelected(true);
    }
    else if (launchSpecificActivity) {
      myLaunchCustomButton.setSelected(true);
    }
    else {
      myDoNothingButton.setSelected(true);
    }
    myActivityField.setEnabled(launchSpecificActivity);
    myActivityField.getChildComponent().setText(configuration.ACTIVITY_CLASS);
    myDeployAndInstallCheckBox.setSelected(configuration.DEPLOY);
  }

  @Override
  public Component getComponent() {
    return myPanel;
  }

  @Override
  public void applyTo(AndroidRunConfiguration configuration) {
    configuration.ACTIVITY_CLASS = myActivityField.getChildComponent().getText();
    if (myLaunchDefaultButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
    }
    else if (myLaunchCustomButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
    }
    else {
      configuration.MODE = AndroidRunConfiguration.DO_NOTHING;
    }
    configuration.DEPLOY = myDeployAndInstallCheckBox.isSelected();
  }

  @Override
  public JComponent getAnchor() {
    return null;
  }

  @Override
  public void setAnchor(JComponent anchor) {
  }

  private void createUIComponents() {
    final EditorTextField editorTextField = new LanguageTextField(PlainTextLanguage.INSTANCE, myProject, "") {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

        if (file != null) {
          DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(file, false);
        }
        editor.putUserData(ACTIVITY_CLASS_TEXT_FIELD_KEY, ApplicationRunParameters.this);
        return editor;
      }
    };
    myActivityField = new ComponentWithBrowseButton<EditorTextField>(editorTextField, null);
  }
}
