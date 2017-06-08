/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers.profilingconfig;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CpuProfilingConfigurationsDialog extends SingleConfigurableEditor {

  private Consumer<ProfilingConfiguration> myOnCloseCallback;

  public CpuProfilingConfigurationsDialog(final Project project,
                                          int deviceApi,
                                          ProfilingConfiguration preSelectedConfiguration,
                                          Consumer<ProfilingConfiguration> onCloseCallback) {
    super(project, new ProfilingConfigurable(project, preSelectedConfiguration, deviceApi), IdeModalityType.IDE);
    myOnCloseCallback = onCloseCallback;
    setHorizontalStretch(1.3F);
    // TODO: add help button on the bottom-left corner when we have the URL for it.
  }

  @Nullable
  private ProfilingConfiguration getSelectedConfiguration() {
    ProfilingConfigurable configurable = (ProfilingConfigurable)getConfigurable();
    return configurable.getSelectedConfiguration();
  }

  @Override
  public void dispose() {
    ProfilingConfiguration lastSelectedConfig = getSelectedConfiguration();
    super.dispose();
    myOnCloseCallback.accept(lastSelectedConfig);
  }

  private static class ProfilingConfigurable extends BaseConfigurable {

    private static final String ADD = "Add";

    private static final String MOVE_DOWN = "Move Down";

    private static final String MOVE_UP = "Move Up";

    private static final String REMOVE = "Remove";

    /**
     * Horizontal splitter to divide the dialog into a list of configurations and a the key-value configurations panel.
     */
    private final JBSplitter mySplitter = new JBSplitter("ProfilingConfigurable.dividerProportion", 0.3f);

    /**
     * List of profiling configurations of {@link #myProject}.
     */

    @NotNull
    private final JList<ProfilingConfiguration> myConfigurations;

    /**
     * The configurations model contains the custom configurations created by users followed by the default ones.
     * We need to make sure to respect this invariant. The custom configurations can have any order, but their indices
     * must be less than the indices of the default ones. Therefore, the default configuration indexes must be
     * greater than or equal to {@link #getCustomConfigurationCount()}.
     */
    @NotNull
    private final DefaultListModel<ProfilingConfiguration> myConfigurationsModel;

    private int myDefaultConfigurationsCount;

    private final Project myProject;

    /**
     * Panel containing key-value CPU profiling settings.
     */
    private CpuProfilingConfigPanel myProfilersPanel;

    public ProfilingConfigurable(Project project, ProfilingConfiguration preSelectedConfiguration, int deviceApi) {
      myProject = project;
      myProfilersPanel = new CpuProfilingConfigPanel(deviceApi >= AndroidVersion.VersionCodes.O);

      myConfigurationsModel = new DefaultListModel<>();
      myConfigurations = new JBList<>(myConfigurationsModel);
      setUpConfigurationsList();
      selectConfiguration(preSelectedConfiguration);
    }

    private void setUpConfigurationsList() {
      myConfigurations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      // TODO: finish customizing cell renderer
      myConfigurations.setCellRenderer(new ListCellRendererWrapper<ProfilingConfiguration>() {
        @Override
        public void customize(JList list,
                              ProfilingConfiguration value,
                              int index,
                              boolean selected,
                              boolean hasFocus) {
          String cellText = value.getName();
          if (index >= getCustomConfigurationCount()) {
            cellText += " - Default";
          }
          setText(cellText);
        }
      });
      myConfigurations.addListSelectionListener((e) -> {
        int index = myConfigurations.getSelectedIndex();
        myProfilersPanel.setConfiguration(index < 0 ? null : myConfigurationsModel.get(index), index >= getCustomConfigurationCount());
      });

      // Restore saved configurations
      for (ProfilingConfiguration configuration : CpuProfilingConfigService.getInstance(myProject).getConfigurations()) {
        // We don't check for device API when listing the configurations. The user should be able to view and the simpleperf configurations,
        // besides the fact they can't select them to profile devices older than O.
        if (configuration.getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLE_PERF
            && !StudioFlags.PROFILER_USE_SIMPLEPERF.get()) {
          continue; // Don't add simpleperf configurations if flag is disabled.
        }
        myConfigurationsModel.addElement(configuration);
      }

      // Add default configurations
      int defaultConfigCount = 0;
      for (ProfilingConfiguration configuration : ProfilingConfiguration.getDefaultProfilingConfigurations()) {
        if (configuration.getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLE_PERF
            && !StudioFlags.PROFILER_USE_SIMPLEPERF.get()) {
          continue; // Don't add simpleperf default configurations if flag is disabled.
        }
        myConfigurationsModel.addElement(configuration);
        defaultConfigCount++;
      }
      myDefaultConfigurationsCount = defaultConfigCount;
    }

    private int getCustomConfigurationCount() {
      return myConfigurationsModel.size() - myDefaultConfigurationsCount;
    }

    private void selectConfiguration(ProfilingConfiguration configuration) {
      for (int i = 0; i < myConfigurationsModel.size(); i++) {
        if (configuration.getName().equals(myConfigurationsModel.get(i).getName())) {
          myConfigurations.setSelectedIndex(i);
          return;
        }
      }
    }

    public ProfilingConfiguration getSelectedConfiguration() {
      return myConfigurations.getSelectedValue();
    }

    private JComponent createLeftPanel() {
      MyAddAction addAction = new MyAddAction();
      MyRemoveAction removeAction = new MyRemoveAction();
      MyMoveAction moveUpAction = new MyMoveAction(MOVE_UP, -1, IconUtil.getMoveUpIcon());
      MyMoveAction moveDownAction = new MyMoveAction(MOVE_DOWN, 1, IconUtil.getMoveUpIcon());

      ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myConfigurations).setAsUsualTopToolbar()
        .setMoveUpAction(moveUpAction).setMoveUpActionUpdater(moveUpAction).setMoveUpActionName(MOVE_UP)
        .setMoveDownAction(moveDownAction).setMoveDownActionUpdater(moveDownAction).setMoveDownActionName(MOVE_DOWN)
        .setRemoveAction(removeAction).setRemoveActionUpdater(removeAction).setRemoveActionName(REMOVE)
        .setAddAction(addAction).setAddActionUpdater(addAction).setAddActionName(ADD)
        .setMinimumSize(new JBDimension(200, 200))
        .setForcedDnD();
      return toolbarDecorator.createPanel();
    }

    @Nls
    @Override
    public String getDisplayName() {
      return "CPU Recording Configurations";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      JPanel mainComponent = new JPanel(new BorderLayout());
      mySplitter.setFirstComponent(createLeftPanel());
      mySplitter.setHonorComponentsMinimumSize(true);
      mySplitter.setSecondComponent(myProfilersPanel.getComponent());
      mainComponent.add(mySplitter, BorderLayout.CENTER);
      mainComponent.setPreferredSize(new Dimension(800, 600));
      return mainComponent;
    }

    @Override
    public void apply() throws ConfigurationException {
      CpuProfilingConfigService profilingConfigService = CpuProfilingConfigService.getInstance(myProject);
      List<ProfilingConfiguration> configsToSave = new ArrayList<>();
      for (int i = 0; i < getCustomConfigurationCount(); i++) {
        configsToSave.add(myConfigurationsModel.get(i));
      }

      List<ProfilingConfiguration> defaultConfigs = ProfilingConfiguration.getDefaultProfilingConfigurations();

      // Check for configs with repeated names
      Set<String> configNames = new HashSet<>();
      for (ProfilingConfiguration config : configsToSave) {
        String configName = config.getName();
        if (configNames.contains(configName)) {
          throw new ConfigurationException("Configuration with name \"" + configName + "\" already exists.");
        }

        for (ProfilingConfiguration defaultConfig : defaultConfigs) {
          if (configName.equals(defaultConfig.getName())) {
            throw new ConfigurationException("\"" + configName + "\" is already being used as a default configuration." +
                                             " Please choose another name.");
          }
        }

        configNames.add(configName);
      }

      profilingConfigService.setConfigurations(configsToSave);
    }

    @Override
    public boolean isModified() {
      // TODO: Handle that properly.
      return true;
    }

    /**
     * Action to add a new configuration to the list.
     */
    private class MyAddAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

      public MyAddAction() {
        super("Add Configuration", "Add a new configuration", IconUtil.getAddIcon());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        addConfiguration();
      }

      @Override
      public void run(AnActionButton button) {
        addConfiguration();
      }

      @Override
      public boolean isEnabled(AnActionEvent e) {
        return true;
      }

      private void addConfiguration() {
        // TODO: generate sequential names (e.g. Unnamed (1), Unnamed (2), ...) instead of repeated names by default.
        ProfilingConfiguration configuration = new ProfilingConfiguration("Unnamed",
                                                                          CpuProfiler.CpuProfilerType.ART,
                                                                          CpuProfiler.CpuProfilingAppStartRequest.Mode.SAMPLED);
        int lastConfigurationIndex = getCustomConfigurationCount();
        myConfigurationsModel.insertElementAt(configuration, lastConfigurationIndex);
        // Select the newly added configuration
        myConfigurations.setSelectedIndex(lastConfigurationIndex);
      }
    }

    /**
     * Action to remove an existing configuration from the list.
     */
    private class MyRemoveAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

      public MyRemoveAction() {
        super("Remove Configuration", "Remove the selected configuration", IconUtil.getRemoveIcon());
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        removeSelectedConfiguration();
      }

      @Override
      public void run(AnActionButton button) {
        removeSelectedConfiguration();
      }

      @Override
      public boolean isEnabled(AnActionEvent e) {
        return nonDefaultSelectionExists();
      }

      /**
       * Whether there is a configuration selected and it's not a default one.
       */
      private boolean nonDefaultSelectionExists() {
        int index = myConfigurations.getSelectedIndex();
        return myConfigurations.getSelectedIndex() >= 0 && index < getCustomConfigurationCount();
      }

      private void removeSelectedConfiguration() {
        if (nonDefaultSelectionExists()) {
          int removedIndex = myConfigurations.getSelectedIndex();
          myConfigurationsModel.remove(removedIndex);
          myConfigurations.setSelectedIndex(removedIndex);
        }
      }
    }

    /**
     * Moves the position an existing configuration in the configurations list.
     */
    private class MyMoveAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

      private int myMoveDownCount;

      public MyMoveAction(String actionText, int moveDownCount, Icon icon) {
        super(actionText, null, icon);
        myMoveDownCount = moveDownCount;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        moveSelectedElement();
      }

      @Override
      public void run(AnActionButton button) {
        moveSelectedElement();
      }

      @Override
      public boolean isEnabled(AnActionEvent e) {
        return validSelectionExists();
      }

      private void moveSelectedElement() {
        if (validSelectionExists()) {
          int origin = myConfigurations.getSelectedIndex();
          int dest = origin + myMoveDownCount;
          ProfilingConfiguration temp = myConfigurationsModel.get(origin);
          myConfigurationsModel.set(origin, myConfigurationsModel.get(dest));
          myConfigurationsModel.set(dest, temp);
          myConfigurations.setSelectedIndex(dest);
        }
      }

      private boolean validSelectionExists() {
        if (myConfigurations.getSelectedIndex() < 0) {
          return false; // Nothing is selected
        }
        if (myConfigurations.getSelectedIndex() >= getCustomConfigurationCount()) {
          return false; // Default configuration is selected.
        }
        if (myConfigurations.getSelectedIndex() + myMoveDownCount < 0 ||
            myConfigurations.getSelectedIndex() + myMoveDownCount >= getCustomConfigurationCount()) {
          return false; // Selected element is already on the first/last available position.
        }
        return true;
      }
    }
  }
}
