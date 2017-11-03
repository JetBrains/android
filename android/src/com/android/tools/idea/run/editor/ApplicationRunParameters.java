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

package com.android.tools.idea.run.editor;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.impl.ConfigurationSettingsEditorWrapper;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEEP_LINK;

public class ApplicationRunParameters<T extends AndroidRunConfiguration> implements ConfigurationSpecificEditor<T>, ActionListener {
  private JPanel myPanel;

  // Deploy options
  private ComboBox myDeployOptionCombo;
  private LabeledComponent<ComboBox> myCustomArtifactLabeledComponent;
  private final ComboBox myArtifactCombo;
  private LabeledComponent<JBTextField> myPmOptionsLabeledComponent;

  // Launch options
  private ComboBox myLaunchOptionCombo;
  private ConfigurableCardPanel myLaunchOptionsCardPanel;
  private LabeledComponent<JBTextField> myAmOptionsLabeledComponent;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private Artifact myLastSelectedArtifact;

  private final ImmutableMap<String, LaunchConfigurableWrapper> myConfigurables;

  public ApplicationRunParameters(final Project project, final ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myDeployOptionCombo.setModel(new CollectionComboBoxModel(Arrays.asList(InstallOption.values())));
    myDeployOptionCombo.setRenderer(new InstallOption.Renderer());
    myDeployOptionCombo.addActionListener(this);
    myDeployOptionCombo.setSelectedItem(InstallOption.DEFAULT_APK);

    myArtifactCombo = myCustomArtifactLabeledComponent.getComponent();
    myArtifactCombo.setRenderer(new ArtifactRenderer());
    myArtifactCombo.setModel(new DefaultComboBoxModel(getAndroidArtifacts().toArray()));
    myArtifactCombo.addActionListener(this);

    myPmOptionsLabeledComponent.getComponent().getEmptyText().setText("Options to 'pm install' command");

    myLaunchOptionCombo.setModel(new CollectionComboBoxModel(AndroidRunConfiguration.LAUNCH_OPTIONS));
    myLaunchOptionCombo.setRenderer(new LaunchOption.Renderer());
    myLaunchOptionCombo.addActionListener(this);

    myAmOptionsLabeledComponent.getComponent().getEmptyText().setText("Options to 'am start' command");

    LaunchOptionConfigurableContext context = new LaunchOptionConfigurableContext() {
      @Nullable
      @Override
      public Module getModule() {
        return myModuleSelector.getModule();
      }
    };

    ImmutableMap.Builder<String, LaunchConfigurableWrapper> builder = ImmutableMap.builder();
    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      builder.put(option.getId(), new LaunchConfigurableWrapper(project, context, option));
    }
    myConfigurables = builder.build();

    myLaunchOptionCombo.setSelectedItem(DefaultActivityLaunch.INSTANCE);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source == myDeployOptionCombo) {
      InstallOption option = (InstallOption)myDeployOptionCombo.getSelectedItem();
      myCustomArtifactLabeledComponent.setVisible(option == InstallOption.CUSTOM_ARTIFACT);
      myPmOptionsLabeledComponent.setVisible(option != InstallOption.NOTHING);

      if (option == InstallOption.CUSTOM_ARTIFACT) {
        updateBuildArtifactBeforeRunSetting();
      }
    }
    else if (source == myArtifactCombo) {
      updateBuildArtifactBeforeRunSetting();
    }
    else if (source == myLaunchOptionCombo) {
      LaunchOption option = (LaunchOption)myLaunchOptionCombo.getSelectedItem();
      myAmOptionsLabeledComponent.setVisible(option != NoLaunch.INSTANCE);
      myLaunchOptionsCardPanel.select(myConfigurables.get(option.getId()), true);
    }
  }

  @Nullable
  public Module getModule() {
    return myModuleSelector.getModule();
  }

  @NotNull
  private static InstallOption getDeployOption(boolean deploy, @Nullable String artifactName) {
    if (!deploy) {
      return InstallOption.NOTHING;
    }

    return StringUtil.isEmpty(artifactName) ? InstallOption.DEFAULT_APK : InstallOption.CUSTOM_ARTIFACT;
  }

  @Override
  public void resetFrom(@NotNull AndroidRunConfiguration configuration) {
    InstallOption installOption = getDeployOption(configuration.DEPLOY, configuration.ARTIFACT_NAME);
    myDeployOptionCombo.setSelectedItem(installOption);

    if (installOption == InstallOption.CUSTOM_ARTIFACT) {
      String artifactName = StringUtil.notNullize(configuration.ARTIFACT_NAME);
      List<Artifact> artifacts = Lists.newArrayList(getAndroidArtifacts());
      Artifact selectedArtifact = findArtifactByName(artifacts, artifactName);

      if (selectedArtifact != null) {
        myArtifactCombo.setModel(new DefaultComboBoxModel(artifacts.toArray()));
        myArtifactCombo.setSelectedItem(selectedArtifact);
      }
      else {
        List<Object> items = Lists.newArrayList(artifacts.toArray());
        items.add(artifactName);
        myArtifactCombo.setModel(new DefaultComboBoxModel(items.toArray()));
        myArtifactCombo.setSelectedItem(artifactName);
      }
    }

    myPmOptionsLabeledComponent.getComponent().setText(configuration.PM_INSTALL_OPTIONS);

    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      LaunchOptionState state = configuration.getLaunchOptionState(option.getId());
      assert state != null : "State is null for option: " + option.getDisplayName();
      myConfigurables.get(option.getId()).resetFrom(state);
    }

    LaunchOption launchOption = getLaunchOption(configuration.MODE);
    myLaunchOptionCombo.setSelectedItem(launchOption);
    myAmOptionsLabeledComponent.getComponent().setText(configuration.ACTIVITY_EXTRA_FLAGS);
  }

  @NotNull
  private static LaunchOption getLaunchOption(@Nullable String mode) {
    if (StringUtil.isEmpty(mode)) {
      mode = DefaultActivityLaunch.INSTANCE.getId();
    }

    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      if (option.getId().equals(mode)) {
        return option;
      }
    }

    throw new IllegalStateException("Unexpected error determining launch mode");
  }

  @Override
  public void applyTo(@NotNull AndroidRunConfiguration configuration) {
    InstallOption installOption = (InstallOption)myDeployOptionCombo.getSelectedItem();
    configuration.DEPLOY = installOption != InstallOption.NOTHING;
    configuration.ARTIFACT_NAME = "";
    if (installOption == InstallOption.CUSTOM_ARTIFACT) {
      Object item = myCustomArtifactLabeledComponent.getComponent().getSelectedItem();
      if (item instanceof Artifact) {
        configuration.ARTIFACT_NAME = ((Artifact)item).getName();
      }
    }
    configuration.PM_INSTALL_OPTIONS = StringUtil.notNullize(myPmOptionsLabeledComponent.getComponent().getText());

    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      LaunchOptionState state = configuration.getLaunchOptionState(option.getId());
      assert state != null : "State is null for option: " + option.getDisplayName();
      myConfigurables.get(option.getId()).applyTo(state);
    }

    LaunchOption launchOption = (LaunchOption)myLaunchOptionCombo.getSelectedItem();
    configuration.MODE = launchOption.getId();
    configuration.ACTIVITY_EXTRA_FLAGS = StringUtil.notNullize(myAmOptionsLabeledComponent.getComponent().getText());
  }

  @Override
  public Component getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getAnchor() {
    return null;
  }

  @Override
  public void setAnchor(JComponent anchor) {
  }

  private void updateBuildArtifactBeforeRunSetting() {
    Artifact newArtifact = null;
    final Object item = myArtifactCombo.getSelectedItem();
    if (item instanceof Artifact) {
      newArtifact = (Artifact)item;
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
      if (makeTasks.isEmpty()) {
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

  @NotNull
  private Collection<? extends Artifact> getAndroidArtifacts() {
    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    return artifactManager == null
           ? Collections.<Artifact>emptyList()
           : artifactManager.getArtifactsByType(AndroidApplicationArtifactType.getInstance());
  }

  @Nullable
  private static Artifact findArtifactByName(@NotNull List<Artifact> artifacts, @NotNull String artifactName) {
    for (Artifact artifact : artifacts) {
      if (artifactName.equals(artifact.getName())) {
        return artifact;
      }
    }

    return null;
  }

  public void onModuleChanged() {
    Module currentModule = myModuleSelector.getModule();

    if (currentModule == null) {
      return;
    }

    // Lock and hide subset of UI when attached to an instantApp
    AndroidModuleModel model = AndroidModuleModel.get(currentModule);
    boolean isInstantApp = false;
    if (model != null && model.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      myLaunchOptionCombo.setSelectedItem(LAUNCH_DEEP_LINK);
      myDeployOptionCombo.setSelectedItem(InstallOption.DEFAULT_APK);
      isInstantApp = true;
    }
    myDeployOptionCombo.setEnabled(!isInstantApp);
    myCustomArtifactLabeledComponent.setEnabled(!isInstantApp);

    myLaunchOptionCombo.setEnabled(!isInstantApp);
  }

  private static class ArtifactRenderer extends ListCellRendererWrapper {
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
  }
}
