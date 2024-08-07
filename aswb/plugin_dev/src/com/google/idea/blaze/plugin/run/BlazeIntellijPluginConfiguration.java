/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin.run;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.plugin.IntellijPluginRule;
import com.google.idea.blaze.plugin.run.BlazeIntellijPluginDeployer.DeployedPluginInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.LogFileOptions;
import com.intellij.execution.configurations.ModuleRunConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.PlatformUtils;
import com.intellij.util.execution.ParametersListUtil;
import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import org.jdom.Element;

/**
 * A run configuration that builds a plugin jar via blaze, copies it to the SDK sandbox, then runs
 * IJ with the plugin loaded.
 */
public class BlazeIntellijPluginConfiguration extends LocatableConfigurationBase<Object>
    implements BlazeRunConfiguration, ModuleRunConfiguration {

  private static final String TARGET_TAG = "blaze-target";
  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";
  private static final String SDK_ATTR = "blaze-plugin-sdk";
  private static final String VM_PARAMS_ATTR = "blaze-vm-params";
  private static final String PROGRAM_PARAMS_ATTR = "blaze-program-params";
  private static final String KEEP_IN_SYNC_TAG = "keep-in-sync";

  private final String buildSystem;

  @Nullable private volatile Label target;
  private RunConfigurationFlagsState blazeFlags;
  private RunConfigurationFlagsState exeFlags;
  @Nullable private Sdk pluginSdk;
  @Nullable String vmParameters;
  @Nullable private String programParameters;

  // for keeping imported configurations in sync with their source XML
  @Nullable private Boolean keepInSync = null;

  public BlazeIntellijPluginConfiguration(
      Project project, ConfigurationFactory factory, String name, @Nullable Label initialTarget) {
    super(project, factory, name);
    this.buildSystem = Blaze.buildSystemName(project);
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (IdeaJdkHelper.isIdeaJdk(projectSdk)) {
      pluginSdk = projectSdk;
    }
    target = initialTarget;
    blazeFlags = new RunConfigurationFlagsState(USER_BLAZE_FLAG_TAG, buildSystem + " flags:");
    exeFlags = new RunConfigurationFlagsState(USER_EXE_FLAG_TAG, "Executable flags:");
  }

  @Override
  public void setKeepInSync(@Nullable Boolean keepInSync) {
    this.keepInSync = keepInSync;
  }

  @Override
  @Nullable
  public Boolean getKeepInSync() {
    return keepInSync;
  }

  @Override
  public ImmutableList<Label> getTargets() {
    Label target = this.target;
    return target == null ? ImmutableList.of() : ImmutableList.of(target);
  }

  public void setTarget(Label target) {
    this.target = target;
  }

  public void setPluginSdk(Sdk sdk) {
    if (IdeaJdkHelper.isIdeaJdk(sdk)) {
      pluginSdk = sdk;
    }
  }

  @Override
  public ArrayList<LogFileOptions> getAllLogFiles() {
    ArrayList<LogFileOptions> result = new ArrayList<>();
    if (pluginSdk == null) {
      return result;
    }
    String sandboxHome = IdeaJdkHelper.getSandboxHome(pluginSdk);
    String logFile = Paths.get(sandboxHome, "system", "log", "idea.log").toString();
    LogFileOptions logFileOptions = new LogFileOptions("idea.log", logFile, true, true, true);
    result.add(logFileOptions);
    return result;
  }

  /**
   * Plugin jar has been previously created via blaze build. This method: - copies jar to sandbox
   * environment - cracks open jar and finds plugin.xml (with ID, etc., needed for JVM args) - sets
   * up the SDK, etc. (use project SDK?) - sets up the JVM, and returns a JavaCommandLineState
   */
  @Nullable
  @Override
  public RunProfileState getState(Executor executor, ExecutionEnvironment env)
      throws ExecutionException {
    final Sdk ideaJdk = pluginSdk;
    if (!IdeaJdkHelper.isIdeaJdk(ideaJdk)) {
      throw new ExecutionException("Choose an IntelliJ Platform Plugin SDK");
    }
    String sandboxHome = IdeaJdkHelper.getSandboxHome(ideaJdk);
    if (sandboxHome == null) {
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK");
    }

    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    } catch (IOException e) {
      throw new ExecutionException("No sandbox specified for IntelliJ Platform Plugin SDK", e);
    }
    String buildNumber = IdeaJdkHelper.getBuildNumber(ideaJdk);
    final BlazeIntellijPluginDeployer deployer = new BlazeIntellijPluginDeployer(sandboxHome);
    env.putUserData(BlazeIntellijPluginDeployer.USER_DATA_KEY, deployer);

    // copy license from running instance of idea
    IdeaJdkHelper.copyIDEALicense(sandboxHome);

    return new JavaCommandLineState(env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        DeployedPluginInfo deployedPluginInfo = deployer.deployNonBlocking(buildSystem);

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        fillParameterList(vm, vmParameters);
        fillParameterList(params.getProgramParametersList(), programParameters);

        IntellijWithPluginClasspathHelper.addRequiredVmParams(
            params, ideaJdk, deployedPluginInfo.javaAgents);

        if (!vm.hasProperty(PlatformUtils.PLATFORM_PREFIX_KEY) && buildNumber != null) {
          String prefix = IdeaJdkHelper.getPlatformPrefix(buildNumber);
          if (prefix != null) {
            vm.defineProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
          }
        }
        return params;
      }

      /** https://youtrack.jetbrains.com/issue/IDEA-201733 */
      @Override
      protected GeneralCommandLine createCommandLine() throws ExecutionException {
        GeneralCommandLine commandLine = super.createCommandLine();
        for (String jreName : new String[] {"jbr", "jre64", "jre"}) {
          File bundledJre = new File(ideaJdk.getHomePath(), jreName);
          if (bundledJre.isDirectory()) {
            File bundledJava = new File(bundledJre, "bin/java");
            if (bundledJava.canExecute()) {
              commandLine.setExePath(bundledJava.getAbsolutePath());
              break;
            }
          }
        }
        return commandLine;
      }

      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        deployer.blockUntilDeployComplete();
        final OSProcessHandler handler = super.startProcess();
        handler.addProcessListener(
            new ProcessAdapter() {
              @Override
              public void processTerminated(ProcessEvent event) {
                deployer.deleteDeployment();
              }
            });
        return handler;
      }
    };
  }

  private static void fillParameterList(ParametersList list, @Nullable String parameters) {
    if (parameters == null) {
      return;
    }
    list.addAll(ParametersListUtil.parse(parameters, /* keepQuotes= */ false));
  }

  @Override
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    Label label = target;
    if (label == null) {
      throw new RuntimeConfigurationError("Select a target to run");
    }
    TargetInfo target = TargetFinder.findTargetInfo(getProject(), label);
    if (target == null) {
      throw new RuntimeConfigurationError("The selected target does not exist.");
    }
    if (!IntellijPluginRule.isPluginTarget(target)) {
      throw new RuntimeConfigurationError("The selected target is not an intellij_plugin");
    }
    if (!IdeaJdkHelper.isIdeaJdk(pluginSdk)) {
      throw new RuntimeConfigurationError("Select an IntelliJ Platform Plugin SDK");
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    // Target is persisted as a tag to permit multiple targets in the future.
    Element targetElement = element.getChild(TARGET_TAG);
    if (targetElement != null && !Strings.isNullOrEmpty(targetElement.getTextTrim())) {
      target = (Label) TargetExpression.fromStringSafe(targetElement.getTextTrim());
    } else {
      target = null;
    }
    blazeFlags.readExternal(element);
    exeFlags.readExternal(element);

    String sdkName = element.getAttributeValue(SDK_ATTR);
    if (!Strings.isNullOrEmpty(sdkName)) {
      pluginSdk = ProjectJdkTable.getInstance().findJdk(sdkName);
    }
    vmParameters = Strings.emptyToNull(element.getAttributeValue(VM_PARAMS_ATTR));
    programParameters = Strings.emptyToNull(element.getAttributeValue(PROGRAM_PARAMS_ATTR));

    String keepInSyncString = element.getAttributeValue(KEEP_IN_SYNC_TAG);
    keepInSync = keepInSyncString != null ? Boolean.parseBoolean(keepInSyncString) : null;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (target != null) {
      // Target is persisted as a tag to permit multiple targets in the future.
      Element targetElement = new Element(TARGET_TAG);
      targetElement.setText(target.toString());
      element.addContent(targetElement);
    }
    blazeFlags.writeExternal(element);
    exeFlags.writeExternal(element);
    if (pluginSdk != null) {
      element.setAttribute(SDK_ATTR, pluginSdk.getName());
    }
    if (vmParameters != null) {
      element.setAttribute(VM_PARAMS_ATTR, vmParameters);
    }
    if (programParameters != null) {
      element.setAttribute(PROGRAM_PARAMS_ATTR, programParameters);
    }
    if (keepInSync != null) {
      element.setAttribute(KEEP_IN_SYNC_TAG, Boolean.toString(keepInSync));
    }
  }

  @Override
  public BlazeIntellijPluginConfiguration clone() {
    final BlazeIntellijPluginConfiguration configuration =
        (BlazeIntellijPluginConfiguration) super.clone();
    configuration.target = target;
    configuration.blazeFlags = blazeFlags.copy();
    configuration.exeFlags = exeFlags.copy();
    configuration.pluginSdk = pluginSdk;
    configuration.vmParameters = vmParameters;
    configuration.programParameters = programParameters;
    configuration.keepInSync = keepInSync;
    return configuration;
  }

  RunConfigurationFlagsState getBlazeFlagsState() {
    return blazeFlags;
  }

  RunConfigurationFlagsState getExeFlagsState() {
    return exeFlags;
  }

  @Override
  public BlazeIntellijPluginConfigurationSettingsEditor getConfigurationEditor() {
    Project project = getProject();
    return new BlazeIntellijPluginConfigurationSettingsEditor(
        findPluginTargets(project), blazeFlags.getEditor(project), exeFlags.getEditor(project));
  }

  private static Set<Label> findPluginTargets(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableSet.of();
    }
    return projectData.targets().stream()
        .filter(IntellijPluginRule::isPluginTarget)
        .map(info -> info.label)
        .collect(toImmutableSet());
  }

  @Override
  @Nullable
  public String suggestedName() {
    Label target = this.target;
    if (target == null) {
      return null;
    }
    return new BlazeConfigurationNameBuilder()
        .setBuildSystemName(getProject())
        .setCommandName("build")
        .setTargetString(target)
        .build();
  }

  @VisibleForTesting
  static class BlazeIntellijPluginConfigurationSettingsEditor
      extends SettingsEditor<BlazeIntellijPluginConfiguration> {
    private final ComboBox<Label> targetCombo;
    private final RunConfigurationStateEditor blazeFlagsEditor;
    private final RunConfigurationStateEditor exeFlagsEditor;
    private final JdkComboBox sdkCombo;
    private final LabeledComponent<RawCommandLineEditor> vmParameters = new LabeledComponent<>();
    private final LabeledComponent<RawCommandLineEditor> programParameters =
        new LabeledComponent<>();
    private final JBCheckBox keepInSyncCheckBox;

    public BlazeIntellijPluginConfigurationSettingsEditor(
        Iterable<Label> javaLabels,
        RunConfigurationStateEditor blazeFlagsEditor,
        RunConfigurationStateEditor exeFlagsEditor) {
      targetCombo =
          new ComboBox<>(
              new DefaultComboBoxModel<>(
                  Ordering.usingToString().sortedCopy(javaLabels).toArray(new Label[0])));
      targetCombo.setRenderer(
          new SimpleListCellRenderer<Label>() {
            @Override
            public void customize(
                JList<? extends Label> list,
                @Nullable Label value,
                int index,
                boolean selected,
                boolean hasFocus) {
              setText(value == null ? null : value.toString());
            }
          });
      this.blazeFlagsEditor = blazeFlagsEditor;
      this.exeFlagsEditor = exeFlagsEditor;
      ProjectSdksModel sdksModel = new ProjectSdksModel();
      sdksModel.reset(null);
      sdkCombo = new JdkComboBox(sdksModel, IdeaJdkHelper::isIdeaJdkType);

      keepInSyncCheckBox = new JBCheckBox("Keep in sync with source XML");
      keepInSyncCheckBox.addItemListener(e -> updateEnabledStatus());
    }

    private void updateEnabledStatus() {
      setEnabled(!keepInSyncCheckBox.isVisible() || !keepInSyncCheckBox.isSelected());
    }

    private void setEnabled(boolean enabled) {
      targetCombo.setEnabled(enabled);
      sdkCombo.setEnabled(enabled);
      vmParameters.getComponent().setEnabled(enabled);
      programParameters.getComponent().setEnabled(enabled);
      blazeFlagsEditor.setComponentEnabled(enabled);
      exeFlagsEditor.setComponentEnabled(enabled);
    }

    @Nullable
    private static Sdk getProjectJdk(@Nullable Sdk possibleClone) {
      return possibleClone == null
          ? null
          : ProjectJdkTable.getInstance().findJdk(possibleClone.getName());
    }

    @VisibleForTesting
    @Override
    public void resetEditorFrom(BlazeIntellijPluginConfiguration s) {
      targetCombo.setSelectedItem(s.target);
      blazeFlagsEditor.resetEditorFrom(s.blazeFlags);
      exeFlagsEditor.resetEditorFrom(s.exeFlags);
      if (s.pluginSdk != null) {
        sdkCombo.setSelectedJdk(s.pluginSdk);
      } else {
        s.pluginSdk = getProjectJdk(sdkCombo.getSelectedJdk());
      }
      if (s.vmParameters != null) {
        vmParameters.getComponent().setText(s.vmParameters);
      }
      if (s.programParameters != null) {
        programParameters.getComponent().setText(s.programParameters);
      }
      keepInSyncCheckBox.setVisible(s.keepInSync != null);
      if (s.keepInSync != null) {
        keepInSyncCheckBox.setSelected(s.keepInSync);
      }
    }

    @VisibleForTesting
    @Override
    public void applyEditorTo(BlazeIntellijPluginConfiguration s) throws ConfigurationException {
      try {
        s.target = (Label) targetCombo.getSelectedItem();
      } catch (ClassCastException e) {
        throw new ConfigurationException("Invalid label specified.");
      }
      blazeFlagsEditor.applyEditorTo(s.blazeFlags);
      exeFlagsEditor.applyEditorTo(s.exeFlags);
      s.pluginSdk = getProjectJdk(sdkCombo.getSelectedJdk());
      s.vmParameters = vmParameters.getComponent().getText();
      s.programParameters = programParameters.getComponent().getText();
      s.keepInSync = keepInSyncCheckBox.isVisible() ? keepInSyncCheckBox.isSelected() : null;
    }

    @Override
    protected JComponent createEditor() {
      vmParameters.setText("VM options:");
      vmParameters.setComponent(new RawCommandLineEditor());
      vmParameters.getComponent().setDialogCaption(vmParameters.getRawText());
      vmParameters.setLabelLocation(BorderLayout.WEST);

      programParameters.setText("Program arguments");
      programParameters.setComponent(new RawCommandLineEditor());
      programParameters.getComponent().setDialogCaption(programParameters.getRawText());
      programParameters.setLabelLocation(BorderLayout.WEST);

      return UiUtil.createBox(
          new JLabel("Target:"),
          targetCombo,
          new JLabel("Plugin SDK"),
          sdkCombo,
          vmParameters.getLabel(),
          vmParameters.getComponent(),
          programParameters.getLabel(),
          programParameters.getComponent(),
          blazeFlagsEditor.createComponent(),
          exeFlagsEditor.createComponent(),
          keepInSyncCheckBox);
    }
  }
}
