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

import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.stdui.menu.CommonMenuItem;
import com.android.tools.adtui.stdui.menu.CommonPopupMenu;
import com.android.tools.adtui.ui.options.OptionsPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.config.ArtInstrumentedConfiguration;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.config.PerfettoSystemTraceConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.SimpleperfConfiguration;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class CpuProfilingConfigurationsDialog extends SingleConfigurableEditor {

  @NotNull private final CpuProfilerConfigModel myProfilerModel;

  @NotNull private Consumer<ProfilingConfiguration> myOnCloseCallback;

  private final int myDeviceLevel;

  public CpuProfilingConfigurationsDialog(@NotNull final Project project,
                                          int deviceLevel,
                                          @NotNull CpuProfilerConfigModel model,
                                          @NotNull Consumer<ProfilingConfiguration> onCloseCallback,
                                          @NotNull FeatureTracker featureTracker,
                                          @NotNull IdeProfilerServices ideProfilerServices) {
    this(project, deviceLevel, model, onCloseCallback,
         new ProfilingConfigurable(project, model, deviceLevel, featureTracker, ideProfilerServices),
         // For task Based UX, don't have the apply button in the EditConfig Dialog.
         !ideProfilerServices.getFeatureConfig().isTaskBasedUxEnabled());
  }

  public CpuProfilingConfigurationsDialog(@NotNull final Project project,
                                          int deviceLevel,
                                          @NotNull CpuProfilerConfigModel model,
                                          @NotNull Consumer<ProfilingConfiguration> onCloseCallback,
                                          @NotNull ProfilingConfigurable profilingConfigurable,
                                          boolean showApplyButton ) {
    super(project, profilingConfigurable, ShowSettingsUtilImpl.createDimensionKey(profilingConfigurable), showApplyButton, IdeModalityType.IDE);
    myProfilerModel = model;
    myOnCloseCallback = onCloseCallback;
    myDeviceLevel = deviceLevel;
    setHorizontalStretch(1.3F);
  }

  @Override
  @VisibleForTesting
  protected Action @NotNull [] createActions() {
    return super.createActions();
  }

  @Nullable
  private ProfilingConfiguration getSelectedConfiguration() {
    ProfilingConfigurable configurable = (ProfilingConfigurable)getConfigurable();
    return configurable.getSelectedConfiguration();
  }

  @Override
  public void dispose() {
    ProfilingConfiguration selectedConfig = myProfilerModel.getProfilingConfiguration();
    if (getExitCode() == OK_EXIT_CODE) {
      selectedConfig = getSelectedConfiguration(); // Call this *before* dispose, otherwise it will be null
    }
    super.dispose();
    // If selected configuration is not supported by the device, we don't try to apply the callback on it.
    boolean selectedConfigSupported = selectedConfig != null && selectedConfig.isDeviceLevelSupported(myDeviceLevel);
    myOnCloseCallback.accept(selectedConfigSupported ? selectedConfig : null);
  }

  @Override
  public void doCancelAction() {
    ProfilingConfigurable configurable = ((ProfilingConfigurable)this.getConfigurable());
    configurable.trackTaskBasedUxConfigMetric(false);
    super.doCancelAction();
  }

  @Override
  protected void doOKAction() {
    ProfilingConfigurable configurable = ((ProfilingConfigurable)this.getConfigurable());
    configurable.trackTaskBasedUxConfigMetric(configurable.isModified());
    super.doOKAction();
  }

  @VisibleForTesting
  public static class ProfilingConfigurable implements Configurable {

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

    @VisibleForTesting
    public DefaultListModel<ProfilingConfiguration> getConfigurationModel() {
      return this.myConfigurationsModel;
    }

    private int myDefaultConfigurationsCount;

    private final Project myProject;

    private final FeatureTracker myFeatureTracker;

    /**
     * Panel containing key-value CPU profiling settings.
     */
    private OptionsPanel myProfilersPanel;

    private CpuProfilerConfigModel myProfilerModel;

    private int myDeviceLevel;

    private boolean isTaskBasedUxEnabled;

    private final IdeProfilerServices myIdeProfilerServices;

    public ProfilingConfigurable(Project project,
                                 CpuProfilerConfigModel model,
                                 int deviceLevel,
                                 FeatureTracker featureTracker,
                                 IdeProfilerServices ideProfilerServices) {
      myProject = project;
      myFeatureTracker = featureTracker;
      myProfilerModel = model;
      myDeviceLevel = deviceLevel;
      myProfilersPanel = new OptionsPanel();
      myConfigurationsModel = new DefaultListModel<>();
      myConfigurations = new JBList<>(myConfigurationsModel);
      myIdeProfilerServices = ideProfilerServices;
      isTaskBasedUxEnabled = ideProfilerServices.getFeatureConfig().isTaskBasedUxEnabled();
      setUpConfigurationsList();
      selectConfiguration(myProfilerModel.getProfilingConfiguration());
    }

    @Nullable
    @Override
    public String getHelpTopic() {
      return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/cpu-recording-configurations-help-link.html";
    }

    private void setUpConfigurationsList() {
      myConfigurations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myConfigurations.setCellRenderer(new ProfilingConfigurationsListCellRenderer());
      myConfigurations.addListSelectionListener((e) -> {
        int index = myConfigurations.getSelectedIndex();
        // Task based ux config fields are always editable
        boolean isReadOnlyConfig = !isTaskBasedUxEnabled && (index >= getCustomConfigurationCount());
        myProfilersPanel.setOption(index < 0 ? null : myConfigurationsModel.get(index), isReadOnlyConfig, isTaskBasedUxEnabled);
      });
      if (isTaskBasedUxEnabled) {
        // Restore saved/default configurations
        for (ProfilingConfiguration configuration : myProfilerModel.getTaskProfilingConfigurations()) {
          // Exclude System Trace config from this list since it doesn't have any updatable attribute
          if (configuration.isEditable()) {
            myConfigurationsModel.addElement(configuration);
          }
        }
      } else {
        // Restore saved configurations
        for (ProfilingConfiguration configuration : myProfilerModel.getCustomProfilingConfigurations()) {
          myConfigurationsModel.addElement(configuration);
        }

        // Add default configurations
        for (ProfilingConfiguration configuration : myProfilerModel.getDefaultProfilingConfigurations()) {
          myConfigurationsModel.addElement(configuration);
        }
        myDefaultConfigurationsCount = myProfilerModel.getDefaultProfilingConfigurations().size();
      }
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

      ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myConfigurations)
        .setToolbarPosition(ActionToolbarPosition.TOP)
        .setPanelBorder(JBUI.Borders.empty())
        .setMinimumSize(new JBDimension(200, 200))
        .setForcedDnD();

      if (!isTaskBasedUxEnabled) {
        toolbarDecorator
          .setMoveUpAction(moveUpAction).setMoveUpActionUpdater(moveUpAction).setMoveUpActionName(MOVE_UP)
          .setMoveDownAction(moveDownAction).setMoveDownActionUpdater(moveDownAction).setMoveDownActionName(MOVE_DOWN)
          .setRemoveAction(removeAction).setRemoveActionUpdater(removeAction).setRemoveActionName(REMOVE)
          .setAddAction(addAction).setAddActionUpdater(addAction).setAddActionName(ADD);
      }
      else {
        // Make the icons invisible
        toolbarDecorator
          .disableRemoveAction()
          .disableDownAction()
          .disableUpAction()
          .disableAddAction();
      }

      JComponent panel = toolbarDecorator.createPanel();
      if (!isTaskBasedUxEnabled) {
        addAction.setPopupParent(panel);
        return panel;
      }

      JPanel taskTitlePanel = new JPanel(new VerticalFlowLayout());
      JPanel headerPanel = new JPanel(new BorderLayout());
      JLabel headerLabel = new JLabel("Tasks");
      headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
      headerPanel.add(headerLabel);
      taskTitlePanel.add(headerPanel);
      taskTitlePanel.add(panel);
      return taskTitlePanel;
    }

    @Nls
    @Override
  public String getDisplayName() {
    if (isTaskBasedUxEnabled) {
        return AndroidBundle.message("configurable.TaskSettings.display.name");
      }
      return AndroidBundle.message("configurable.ProfilingConfigurable.display.name");
  }

    @Nullable
    @Override
    public JComponent createComponent() {
      JPanel mainComponent = new JPanel(new BorderLayout());
      mySplitter.setFirstComponent(createLeftPanel());
      mySplitter.setHonorComponentsMinimumSize(true);
      mySplitter.setSecondComponent(myProfilersPanel);
      mainComponent.add(mySplitter, BorderLayout.CENTER);
      mainComponent.setPreferredSize(new Dimension(800, 600));
      return mainComponent;
    }

    @Override
    public void apply() throws ConfigurationException {

      // Check for configs with repeated names
      Set<String> configNames = new HashSet<>();
      List<CpuProfilerConfig> configsToSave = new ArrayList<>();

      for (int i = 0; i < myConfigurationsModel.getSize(); i++) {
        ProfilingConfiguration config = myConfigurationsModel.getElementAt(i);
        String configName = config.getName();
        if (StringUtil.isEmpty(configName)) {
          throw new ConfigurationException("Empty configuration names are not allowed. Please rename or delete them before continuing.");
        }
        if (configNames.contains(configName)) {
          throw new ConfigurationException("Configuration with name \"" + configName + "\" already exists.");
        }
        configNames.add(configName);

        // Default configs are editable(except name) in task based ux and hence always save to reflect latest update when task-based flag
        // is enabled
        if (!isDefaultConfig(config) || isTaskBasedUxEnabled) {
          configsToSave.add(CpuProfilerConfigConverter.fromProfilingConfiguration(config));
        }
      }

      if (isTaskBasedUxEnabled) {
        for (ProfilingConfiguration configuration : myProfilerModel.getTaskProfilingConfigurations()) {
          // For task based UX, though not displaying the non-editable task config (system trace) in task settings, yet it needs to be
          // saved. If not, it will be skipped from recording options which shouldn't happen.
          if (!configuration.isEditable()) {
            configsToSave.add(CpuProfilerConfigConverter.fromProfilingConfiguration(configuration));
          }
        }
        CpuProfilerConfigsState.getInstance(myProject).setTaskConfigs(configsToSave);
      } else {
        CpuProfilerConfigsState.getInstance(myProject).setUserConfigs(configsToSave);
      }
    }

    private boolean isTaskConfigChanged() {
      // Latest config from dialog
      List<CpuProfilerConfig> latestConfigs = new ArrayList<>();
      for (int i = 0; i < myConfigurationsModel.getSize(); i++) {
        latestConfigs.add(CpuProfilerConfigConverter.fromProfilingConfiguration(myConfigurationsModel.getElementAt(i)));
      }
      List<CpuProfilerConfig> existingConfigs = CpuProfilerConfigsState.getInstance(myProject).getSavedTaskConfigsIfPresentOrDefault();
      Map<String, List<CpuProfilerConfig>>
        existingConfigGrouped = existingConfigs.stream().collect(Collectors.groupingBy(CpuProfilerConfig::getName));
      Map<String, List<CpuProfilerConfig>>
        latestConfigGrouped = latestConfigs.stream().collect(Collectors.groupingBy(CpuProfilerConfig::getName));
      for (String configName : latestConfigGrouped.keySet()) {
        // Fetch only first element of the list, since for task based no additional configs can be created.
        CpuProfilerConfig configNow = latestConfigGrouped.get(configName).get(0);
        CpuProfilerConfig configBefore = existingConfigGrouped.get(configName).get(0);
        if (!configNow.equals(configBefore)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean isModified() {
      // Handling for task based UX
      if (isTaskBasedUxEnabled) {
        return this.isTaskConfigChanged();
      }
      // TODO: Handle that properly.
      return true;
    }

    public void trackTaskBasedUxConfigMetric(boolean isConfigChanged) {
      if (this.isTaskBasedUxEnabled) {
        myIdeProfilerServices.getFeatureTracker().trackTaskSettingsOpened(isConfigChanged);
      }
    }

    private static boolean isDefaultConfig(@NotNull ProfilingConfiguration configuration) {
      return CpuProfilerConfigsState.getDefaultConfigs()
        .stream()
        .anyMatch(c -> c.getName().equals(configuration.getName()));
    }

    private class ProfilingConfigurationsListCellRenderer implements ListCellRenderer<ProfilingConfiguration> {

      /**
       * Label to display the configuration name.
       */
      private JLabel myLabel;

      public ProfilingConfigurationsListCellRenderer() {
        myLabel = new JLabel();
        Border marginLeft = new EmptyBorder(0, 10, 0, 0);
        myLabel.setBorder(marginLeft);
      }

      @Override
      public Component getListCellRendererComponent(JList<? extends ProfilingConfiguration> list,
                                                    ProfilingConfiguration value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 25));
        panel.setBackground(list.getBackground());
        String cellText = value.getName();

        // TODO(b/69367377): Update the design for features that are supported outside the current device level.
        if (!value.isDeviceLevelSupported(myDeviceLevel)) {
          cellText += String.format(Locale.US, " (API Level %d+)", value.getRequiredDeviceLevel());
        }
        myLabel.setText(cellText);
        myLabel.setForeground(isSelected ? Gray._255 : JBColor.BLACK);
        if (isSelected) {
          panel.setBackground(ProfilerColors.CPU_PROFILING_CONFIGURATIONS_SELECTED);
        }
        panel.add(myLabel, BorderLayout.CENTER);

        return panel;
      }
    }

    /**
     * Action to add a new configuration to the list.
     */
    private class MyAddAction extends AnAction implements AnActionButtonRunnable, AnActionButtonUpdater {

      @NotNull private final CommonPopupMenu myPopup;
      @NotNull private final List<CommonAction> myActions = new ArrayList<>();
      private static final String UNNAMED = "Unnamed";
      private JComponent myPanel;
      private MyAddAction() {
        super("Add Configuration", "Add a new configuration", IconUtil.getAddIcon());
        myPopup = new CommonPopupMenu();
        myPopup.add(buildPopupMenuItem(CpuProfilerConfig.Technology.SAMPLED_NATIVE.getName(), SimpleperfConfiguration::new));
        myPopup.add(buildPopupMenuItemPerfetto(CpuProfilerConfig.Technology.SYSTEM_TRACE.getName()));
        myPopup.add(buildPopupMenuItem(CpuProfilerConfig.Technology.INSTRUMENTED_JAVA.getName(), ArtInstrumentedConfiguration::new));
        myPopup.add(buildPopupMenuItem(CpuProfilerConfig.Technology.SAMPLED_JAVA.getName(), ArtSampledConfiguration::new));
      }

      private CommonMenuItem buildPopupMenuItemPerfetto(String name) {
        CommonAction action = new CommonAction(name, null);
        action.setAction(() -> addConfiguration(new PerfettoSystemTraceConfiguration(UNNAMED, StudioFlags.PROFILER_TRACEBOX.get())));
        return new CommonMenuItem(action);
      }

      private CommonMenuItem buildPopupMenuItem(String name, Function<String, ProfilingConfiguration> configurationConstructor) {
        CommonAction action = new CommonAction(name, null);
        action.setAction(() -> addConfiguration(configurationConstructor.apply(UNNAMED)));
        return new CommonMenuItem(action);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }

      public void setPopupParent(JComponent panel) {
        myPanel = panel;
      }

      @Override
      public void run(AnActionButton button) {
        myPopup.show(myPanel, 0, 24);
      }

      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        return true;
      }

      private void addConfiguration(ProfilingConfiguration configuration) {
        int lastConfigurationIndex = getCustomConfigurationCount();
        myConfigurationsModel.insertElementAt(configuration, lastConfigurationIndex);
        // Select the newly added configuration
        myConfigurations.setSelectedIndex(lastConfigurationIndex);
        myFeatureTracker.trackCreateCustomProfilingConfig();
      }

      /**
       * Given "name", returns "name", "name (1)", "name (2)", ..., or whichever first version is unique.
       */
      @NotNull
      private String getUniqueName(@NotNull String name) {
        Set<String> names = new HashSet<>();
        Enumeration<ProfilingConfiguration> configurations = myConfigurationsModel.elements();
        while (configurations.hasMoreElements()) {
          names.add(configurations.nextElement().getName());
        }

        String uniqueName = name;
        int i = 1;
        while (names.contains(uniqueName)) {
          uniqueName = String.format(Locale.US, "%s (%d)", name, i++);
        }
        return uniqueName;
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeSelectedConfiguration();
      }

      @Override
      public void run(AnActionButton button) {
        removeSelectedConfiguration();
      }

      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        moveSelectedElement();
      }

      @Override
      public void run(AnActionButton button) {
        moveSelectedElement();
      }

      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
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
