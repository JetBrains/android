// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run.editor;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;

import com.android.annotations.Nullable;
import com.android.tools.idea.backup.BackupManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.run.RunConfigSection;
import com.android.tools.idea.run.activity.launch.DeepLinkLaunch;
import com.android.tools.idea.run.activity.launch.DefaultActivityLaunch;
import com.android.tools.idea.run.activity.launch.LaunchOption;
import com.android.tools.idea.run.activity.launch.LaunchOptionConfigurableContext;
import com.android.tools.idea.run.activity.launch.LaunchOptionState;
import com.android.tools.idea.run.activity.launch.NoLaunch;
import com.google.common.base.Preconditions;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.SmartList;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

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
  private JComponent myDynamicFeaturesParametersComponent;
  private JBCheckBox myInstantAppDeployCheckBox;
  private JBCheckBox myAllUsersCheckbox;
  private JBCheckBox myAlwaysInstallWithPmCheckbox;
  private JBCheckBox myClearAppStorageCheckbox;
  private JPanel myRestorePanelWrapper;

  private final @Nullable RunConfigSection myRestoreRunConfigSection;

  private final Project myProject;
  private final ConfigurationModuleSelector myModuleSelector;
  private Artifact myLastSelectedArtifact;

  private final ImmutableMap<String, LaunchConfigurableWrapper> myConfigurables;
  private DynamicFeaturesParameters myDynamicFeaturesParameters;

  public ApplicationRunParameters(final Project project, final ConfigurationModuleSelector moduleSelector) {
    myProject = project;
    myModuleSelector = moduleSelector;

    myDeployOptionCombo.setModel(new CollectionComboBoxModel(Arrays.asList(InstallOption.values())));
    myDeployOptionCombo.setRenderer(new InstallOption.Renderer());
    myDeployOptionCombo.addActionListener(this);
    myDeployOptionCombo.setSelectedItem(InstallOption.DEFAULT_APK);

    myArtifactCombo = myCustomArtifactLabeledComponent.getComponent();
    myArtifactCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value instanceof Artifact) {
        final Artifact artifact = (Artifact)value;
        label.setText(artifact.getName());
        label.setIcon(artifact.getArtifactType().getIcon());
      }
      else if (value instanceof String) {
        label.setText("<html><font color='red'>" + value + "</font></html>");
      }
    }));
    myArtifactCombo.setModel(new DefaultComboBoxModel(getAndroidArtifacts().toArray()));
    myArtifactCombo.addActionListener(this);

    myPmOptionsLabeledComponent.getComponent().getEmptyText().setText("Options to 'pm install' command");

    myLaunchOptionCombo.setModel(new CollectionComboBoxModel(AndroidRunConfiguration.LAUNCH_OPTIONS));
    myLaunchOptionCombo.setRenderer(new LaunchOption.Renderer());
    myLaunchOptionCombo.addActionListener(this);

    myAmOptionsLabeledComponent.getComponent().getEmptyText().setText("Options to 'am start' command");

    myInstantAppDeployCheckBox.addActionListener(this);

    LaunchOptionConfigurableContext context = new LaunchOptionConfigurableContext() {
      @Nullable
      @Override
      public Module getModule() {
        Module selectedModule = myModuleSelector.getModule();
        return selectedModule == null ? null : ModuleSystemUtil.getMainModule(selectedModule);
      }
    };

    ImmutableMap.Builder<String, LaunchConfigurableWrapper> builder = ImmutableMap.builder();
    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      builder.put(option.getId(), new LaunchConfigurableWrapper(project, context, option));
    }
    myConfigurables = builder.build();

    myLaunchOptionCombo.setSelectedItem(DefaultActivityLaunch.INSTANCE);

    myInstantAppDeployCheckBox.setVisible(StudioFlags.UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS.get());
    myAlwaysInstallWithPmCheckbox.setVisible(StudioFlags.OPTIMISTIC_INSTALL_SUPPORT_LEVEL.get() != StudioFlags.OptimisticInstallSupportLevel.DISABLED);

    if (StudioFlags.BACKUP_ENABLED.get()) {
      myRestoreRunConfigSection = BackupManager.getInstance(project).getRestoreRunConfigSection(project);
      myRestorePanelWrapper.add(myRestoreRunConfigSection.getComponent(this), BorderLayout.CENTER);
    } else {
      myRestoreRunConfigSection = null;
    }
  }

  private void createUIComponents() {
    myDynamicFeaturesParameters = new DynamicFeaturesParameters();
    myDynamicFeaturesParametersComponent = myDynamicFeaturesParameters.getComponent();
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
    else if (source == myInstantAppDeployCheckBox) {
      if (myModuleSelector.getModule() != null) {
        myDynamicFeaturesParameters.updateBasedOnInstantState(myModuleSelector.getModule(), myInstantAppDeployCheckBox.isSelected());
      }
    }
  }

  /**
   * Returns the {@link InstallOption} given the various deployment option persistent in the run configuration
   * state.
   *
   * @param deploy           {@code true} if deploying APKs to the device
   * @param deployFromBundle {@code true} if deploying APK from the bundle to the device. If {@code true}, the deploy parameter must
   *                         be {@code true} too.
   * @param artifactName     The custom artifact to deploy to the device. If {@code empty},
   */
  @NotNull
  private static InstallOption getDeployOption(boolean deploy, boolean deployFromBundle, @Nullable String artifactName) {
    // deployFromBundle == true implies deploy == true
    Preconditions.checkArgument(!deployFromBundle || deploy);

    if (deploy) {
      if (deployFromBundle) {
        return StringUtil.isEmpty(artifactName) ? InstallOption.APK_FROM_BUNDLE : InstallOption.CUSTOM_ARTIFACT;
      }
      return StringUtil.isEmpty(artifactName) ? InstallOption.DEFAULT_APK : InstallOption.CUSTOM_ARTIFACT;
    }

    return InstallOption.NOTHING;
  }

  @Override
  public void resetFrom(@NotNull AndroidRunConfiguration configuration) {
    InstallOption installOption = getDeployOption(configuration.DEPLOY, configuration.DEPLOY_APK_FROM_BUNDLE, configuration.ARTIFACT_NAME);
    myDeployOptionCombo.setSelectedItem(installOption);

    myInstantAppDeployCheckBox.setSelected(myInstantAppDeployCheckBox.isEnabled() && configuration.DEPLOY_AS_INSTANT);
    Module currentModule = myModuleSelector.getModule();
    if (currentModule != null) {
      myDynamicFeaturesParameters.updateBasedOnInstantState(currentModule, myInstantAppDeployCheckBox.isSelected());
    }

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
    myAllUsersCheckbox.setSelected(configuration.ALL_USERS);
    myAlwaysInstallWithPmCheckbox.setSelected(configuration.ALWAYS_INSTALL_WITH_PM);
    myClearAppStorageCheckbox.setSelected(configuration.CLEAR_APP_STORAGE);

    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      LaunchOptionState state = configuration.getLaunchOptionState(option.getId());
      assert state != null : "State is null for option: " + option.getDisplayName();
      myConfigurables.get(option.getId()).resetFrom(state);
    }

    LaunchOption launchOption = getLaunchOption(configuration.MODE);
    myLaunchOptionCombo.setSelectedItem(launchOption);
    myAmOptionsLabeledComponent.getComponent().setText(configuration.ACTIVITY_EXTRA_FLAGS);
    myDynamicFeaturesParameters.setDisabledDynamicFeatures(configuration.getDisabledDynamicFeatures());

    if (myRestoreRunConfigSection != null) {
      myRestoreRunConfigSection.resetFrom(configuration);
    }
  }

  @Override
  public void applyTo(@NotNull AndroidRunConfiguration configuration) {
    InstallOption installOption = (InstallOption)myDeployOptionCombo.getSelectedItem();
    configuration.DEPLOY = installOption != InstallOption.NOTHING;
    configuration.DEPLOY_APK_FROM_BUNDLE = installOption == InstallOption.APK_FROM_BUNDLE;
    configuration.DEPLOY_AS_INSTANT = myInstantAppDeployCheckBox.isSelected();
    configuration.ARTIFACT_NAME = "";
    if (installOption == InstallOption.CUSTOM_ARTIFACT) {
      Object item = myCustomArtifactLabeledComponent.getComponent().getSelectedItem();
      if (item instanceof Artifact) {
        configuration.ARTIFACT_NAME = ((Artifact)item).getName();
      }
    }
    configuration.PM_INSTALL_OPTIONS = StringUtil.notNullize(myPmOptionsLabeledComponent.getComponent().getText());
    configuration.ALL_USERS = myAllUsersCheckbox.isSelected();
    configuration.ALWAYS_INSTALL_WITH_PM = myAlwaysInstallWithPmCheckbox.isSelected();
    configuration.CLEAR_APP_STORAGE = myClearAppStorageCheckbox.isSelected();

    for (LaunchOption option : AndroidRunConfiguration.LAUNCH_OPTIONS) {
      LaunchOptionState state = configuration.getLaunchOptionState(option.getId());
      assert state != null : "State is null for option: " + option.getDisplayName();
      myConfigurables.get(option.getId()).applyTo(state);
    }

    LaunchOption launchOption = (LaunchOption)myLaunchOptionCombo.getSelectedItem();
    configuration.MODE = launchOption.getId();
    configuration.ACTIVITY_EXTRA_FLAGS = StringUtil.notNullize(myAmOptionsLabeledComponent.getComponent().getText());
    configuration.setDisabledDynamicFeatures(myDynamicFeaturesParameters.getDisabledDynamicFeatures());

    if (myRestoreRunConfigSection != null) {
      myRestoreRunConfigSection.applyTo(configuration);
    } else {
      configuration.RESTORE_ENABLED = false;
    }
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
  public Component getComponent() {
    return myPanel;
  }

  @Override
  public void dispose() {}

  private void updateBuildArtifactBeforeRunSetting() {
    Artifact newArtifact = null;
    final Object item = myArtifactCombo.getSelectedItem();
    if (item instanceof Artifact) {
      newArtifact = (Artifact)item;
    }

    if (Objects.equals(newArtifact, myLastSelectedArtifact)) {
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
    final List<BeforeRunTask> makeTasks = new SmartList<>();
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
    final ArtifactType androidArtifactType = ArtifactType.findById("apk");
    return artifactManager == null || androidArtifactType == null
           ? Collections.emptyList()
           : artifactManager.getArtifactsByType(androidArtifactType);
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
      // Disable and deselect instant deploy checkbox if <no module> is selected.
      myInstantAppDeployCheckBox.setEnabled(false);
      myInstantAppDeployCheckBox.setSelected(false);
      return;
    }

    // Lock and hide subset of UI when attached to an instantApp
    AndroidFacet facet = AndroidFacet.getInstance(currentModule);
    AndroidModel model = AndroidModel.get(currentModule);
    boolean isInstantApp = facet != null && facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP;
    if (isInstantApp) {
      myLaunchOptionCombo.setSelectedItem(DeepLinkLaunch.INSTANCE);
      myDeployOptionCombo.setSelectedItem(InstallOption.DEFAULT_APK);
    }
    else {
      // Enable instant app deploy checkbox if module is instant enabled
      myInstantAppDeployCheckBox.setEnabled(model != null && model.isInstantAppCompatible());
      // If the module is not instant-eligible, uncheck the checkbox.
      if (model == null || !model.isInstantAppCompatible()) {
        myInstantAppDeployCheckBox.setSelected(false);
      }

      myLaunchOptionCombo.setSelectedItem(DefaultActivityLaunch.INSTANCE);
    }

    myDeployOptionCombo.setEnabled(!isInstantApp);
    myCustomArtifactLabeledComponent.setEnabled(!isInstantApp);

    myLaunchOptionCombo.setEnabled(!isInstantApp);
    myDynamicFeaturesParameters.setActiveModule(currentModule,
                                                (model != null && model.isInstantAppCompatible()
                                                 && StudioFlags.UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS.get())
                                                ? DynamicFeaturesParameters.AvailableDeployTypes.INSTANT_AND_INSTALLED
                                                : DynamicFeaturesParameters.AvailableDeployTypes.INSTALLED_ONLY);
  }
}
