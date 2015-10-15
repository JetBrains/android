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
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactType;
import org.jetbrains.android.compiler.artifact.AndroidArtifactUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 27, 2009
 * Time: 4:15:59 PM
 * To change this template use File | Settings | File Templates.
 */
public class ApplicationRunParameters<T extends AndroidRunConfiguration> implements ConfigurationSpecificEditor<T> {
  public static final Key<ApplicationRunParameters> ACTIVITY_CLASS_TEXT_FIELD_KEY = Key.create("ActivityClassTextField");

  private ComponentWithBrowseButton<EditorTextField> myActivityField;
  private JTextField myActivityExtraFlagsField;
  private JRadioButton myLaunchDefaultButton;
  private JRadioButton myLaunchCustomButton;
  private JPanel myPanel;
  private JRadioButton myDoNothingButton;
  private JBRadioButton myDeployDefaultApkRadio;
  private JBRadioButton myDeployArtifactRadio;
  private JBRadioButton myDoNotDeployRadio;
  private ComboBox myArtifactCombo;
  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private Artifact myLastSelectedArtifact;

  public ApplicationRunParameters(final Project project, final ConfigurationModuleSelector moduleSelector) {
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
        myActivityExtraFlagsField.setEnabled(!myDoNothingButton.isSelected());
      }
    };
    myLaunchCustomButton.addActionListener(listener);
    myLaunchDefaultButton.addActionListener(listener);
    myDoNothingButton.addActionListener(listener);

    final ActionListener listener1 = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myArtifactCombo.setEnabled(myDeployArtifactRadio.isSelected());
        updateBuildArtifactBeforeRunSetting();
      }
    };
    myDeployDefaultApkRadio.addActionListener(listener1);
    myDoNotDeployRadio.addActionListener(listener1);
    myDeployArtifactRadio.addActionListener(listener1);

    myArtifactCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateBuildArtifactBeforeRunSetting();
      }
    });
  }

  private void updateBuildArtifactBeforeRunSetting() {
    Artifact newArtifact = null;

    if (myDeployArtifactRadio.isSelected()) {
      final Object item = myArtifactCombo.getSelectedItem();

      if (item instanceof Artifact) {
        newArtifact = (Artifact)item;
      }
    }
    if (Comparing.equal(newArtifact, myLastSelectedArtifact)) {
      return;
    }
    if (myLastSelectedArtifact != null) {
      BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRunOption(myPanel, myProject, myLastSelectedArtifact, false);
    }
    if (newArtifact != null) {
      BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRunOption(myPanel, myProject, newArtifact, true);
    }

    if (myLastSelectedArtifact == null || newArtifact == null) {
      addOrRemoveMakeTask(newArtifact == null);
    }
    myLastSelectedArtifact = newArtifact;
  }

  private void addOrRemoveMakeTask(boolean add) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(myPanel);
    final ConfigurationSettingsEditorWrapper editor = ConfigurationSettingsEditorWrapper.CONFIGURATION_EDITOR_KEY.getData(dataContext);

    if (editor == null) {
      return;
    }
    final List<BeforeRunTask> makeTasks = new ArrayList<BeforeRunTask>();

    for (BeforeRunTask task : editor.getStepsBeforeLaunch()) {
      if (task instanceof CompileStepBeforeRun.MakeBeforeRunTask ||
          task instanceof CompileStepBeforeRunNoErrorCheck.MakeBeforeRunTaskNoErrorCheck) {
        makeTasks.add(task);
      }
    }
    if (add) {
      if (makeTasks.size() == 0) {
        editor.addBeforeLaunchStep(new CompileStepBeforeRun.MakeBeforeRunTask());
      }
      else {
        for (BeforeRunTask task : makeTasks) {
          task.setEnabled(true);
        }
      }
    }
    else {
      for (BeforeRunTask task : makeTasks) {
        task.setEnabled(false);
      }
    }
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

    myActivityExtraFlagsField.setEnabled(!configuration.MODE.equals(AndroidRunConfiguration.DO_NOTHING));
    myActivityExtraFlagsField.setText(configuration.ACTIVITY_EXTRA_FLAGS);

    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    final Collection<? extends Artifact> artifacts = artifactManager == null
                                                     ? Collections.<Artifact>emptyList()
                                                     : artifactManager.getArtifactsByType(AndroidApplicationArtifactType.getInstance());
    final String artifactName = configuration.ARTIFACT_NAME;
    Artifact artifactToSelect = null;

    if (configuration.DEPLOY) {
      myDoNotDeployRadio.setSelected(false);

      if (!StringUtil.isEmpty(artifactName)) {
        final Module module = getModule();

        for (Artifact artifact : artifacts) {
          if (artifactName.equals(artifact.getName()) &&
              AndroidArtifactUtil.isRelatedArtifact(artifact, module)) {
            artifactToSelect = artifact;
            break;
          }
        }
        myDeployArtifactRadio.setSelected(true);
        myDeployDefaultApkRadio.setSelected(false);
      }
      else {
        myDeployArtifactRadio.setSelected(false);
        myDeployDefaultApkRadio.setSelected(true);
      }
    }
    else {
      myDeployArtifactRadio.setSelected(false);
      myDeployDefaultApkRadio.setSelected(false);
      myDoNotDeployRadio.setSelected(true);
    }
    myArtifactCombo.setEnabled(myDeployArtifactRadio.isSelected());

    if (artifactToSelect != null || artifactName.length() == 0) {
      myArtifactCombo.setModel(new DefaultComboBoxModel(artifacts.toArray()));

      if (artifactToSelect != null) {
        myArtifactCombo.setSelectedItem(artifactToSelect);
      }
    }
    else {
      final List<Object> list = new ArrayList<Object>();
      list.add(artifactName);
      list.addAll(Arrays.asList(artifacts));
      myArtifactCombo.setModel(new DefaultComboBoxModel(list.toArray()));
      myArtifactCombo.setSelectedItem(artifactName);
    }
    myArtifactCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Artifact) {
          final Artifact artifact = (Artifact)value;
          setText(artifact.getName());
          setIcon(artifact.getArtifactType().getIcon());
        }
        else if (value instanceof String) {
          setText("<html><font color='red'>" + value + "</font></html>");
        }
      }
    });
    final Object item = myDeployArtifactRadio.isSelected() ? myArtifactCombo.getSelectedItem() : null;
    myLastSelectedArtifact = item instanceof Artifact ? (Artifact)item : null;
  }

  @Override
  public Component getComponent() {
    return myPanel;
  }

  @Override
  public void applyTo(AndroidRunConfiguration configuration) {
    configuration.ACTIVITY_CLASS = myActivityField.getChildComponent().getText();
    configuration.ACTIVITY_EXTRA_FLAGS = myActivityExtraFlagsField.getText();
    if (myLaunchDefaultButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
    }
    else if (myLaunchCustomButton.isSelected()) {
      configuration.MODE = AndroidRunConfiguration.LAUNCH_SPECIFIC_ACTIVITY;
    }
    else {
      configuration.MODE = AndroidRunConfiguration.DO_NOTHING;
    }
    configuration.DEPLOY = !myDoNotDeployRadio.isSelected();

    if (myDeployArtifactRadio.isSelected()) {
      final Object item = myArtifactCombo.getSelectedItem();

      if (item instanceof Artifact) {
        final Artifact artifact = (Artifact)item;
        configuration.ARTIFACT_NAME = artifact.getName();
      }
      else {
        configuration.ARTIFACT_NAME = item != null ? item.toString() : "";
      }
    }
    else {
      configuration.ARTIFACT_NAME = "";
    }
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
