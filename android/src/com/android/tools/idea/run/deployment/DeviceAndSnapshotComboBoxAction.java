/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  @VisibleForTesting
  static final String SELECTED_DEVICE = "DeviceAndSnapshotComboBoxAction.selectedDevice";

  private static final String SELECTION_TIME = "DeviceAndSnapshotComboBoxAction.selectionTime";

  /**
   * This <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">persisted value</a> is true when a
   * developer selects Multiple Devices with the combo box and unset otherwise.
   */
  @VisibleForTesting
  static final String MULTIPLE_DEVICES_SELECTED = "DeviceAndSnapshotComboBoxAction.multipleDevicesSelected";

  /**
   * Run configurations that aren't {@link AndroidRunConfiguration} or {@link AndroidTestRunConfiguration} can use this key
   * to express their applicability for DeviceAndSnapshotComboBoxAction by setting it to true in their user data.
   */
  public static final com.intellij.openapi.util.Key<Boolean> DEPLOYS_TO_LOCAL_DEVICE =
    com.intellij.openapi.util.Key.create("DeviceAndSnapshotComboBoxAction.deploysToLocalDevice");

  @NotNull
  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  @NotNull
  private final Function<Project, PropertiesComponent> myGetProperties;

  @NotNull
  private final Clock myClock;

  @NotNull
  private final Function<Project, List<Device>> myGetSelectedDevices;

  @NotNull
  private final Function<Project, RunManager> myGetRunManager;

  @NotNull
  private final Function<Project, ExecutionTargetManager> myGetExecutionTargetManager;

  @NotNull
  private final AnAction myMultipleDevicesAction;

  @NotNull
  private final AnAction myModifyDeviceSetAction;

  @VisibleForTesting
  static final class Builder {
    @Nullable
    private Supplier<Boolean> mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    @Nullable
    private Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

    @Nullable
    private Function<Project, PropertiesComponent> myGetProperties;

    @Nullable
    private Clock myClock;

    @Nullable
    private Function<Project, List<Device>> myGetSelectedDevices;

    @Nullable
    private Function<Project, RunManager> myGetRunManager;

    @Nullable
    private Function<Project, ExecutionTargetManager> myGetExecutionTargetManager;

    Builder() {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = () -> false;
      myDevicesGetterGetter = project -> null;
      myGetProperties = project -> null;
      myGetSelectedDevices = project -> null;
      myGetRunManager = project -> null;
      myGetExecutionTargetManager = project -> null;
    }

    @NotNull
    Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabled(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxSnapshotsEnabled) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabled = selectDeviceSnapshotComboBoxSnapshotsEnabled;
      return this;
    }

    @NotNull
    Builder setDevicesGetterGetter(@NotNull Function<Project, AsyncDevicesGetter> devicesGetterGetter) {
      myDevicesGetterGetter = devicesGetterGetter;
      return this;
    }

    @NotNull
    Builder setGetProperties(@NotNull Function<Project, PropertiesComponent> getProperties) {
      myGetProperties = getProperties;
      return this;
    }

    @NotNull
    Builder setClock(@NotNull Clock clock) {
      myClock = clock;
      return this;
    }

    @NotNull
    Builder setGetSelectedDevices(@NotNull Function<Project, List<Device>> getSelectedDevices) {
      myGetSelectedDevices = getSelectedDevices;
      return this;
    }

    @NotNull
    Builder setGetRunManager(@NotNull Function<Project, RunManager> getRunManager) {
      myGetRunManager = getRunManager;
      return this;
    }

    @NotNull
    Builder setGetExecutionTargetManager(@NotNull Function<Project, ExecutionTargetManager> getExecutionTargetManager) {
      myGetExecutionTargetManager = getExecutionTargetManager;
      return this;
    }

    @NotNull
    DeviceAndSnapshotComboBoxAction build() {
      return new DeviceAndSnapshotComboBoxAction(this);
    }
  }

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxAction() {
    this(new Builder()
           .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED.get())
           .setDevicesGetterGetter(project -> ServiceManager.getService(project, AsyncDevicesGetter.class))
           .setGetProperties(PropertiesComponent::getInstance)
           .setClock(Clock.systemDefaultZone())
           .setGetSelectedDevices(ModifyDeviceSetDialog::getSelectedDevices)
           .setGetRunManager(RunManager::getInstance)
           .setGetExecutionTargetManager(ExecutionTargetManager::getInstance));
  }

  @NonInjectable
  private DeviceAndSnapshotComboBoxAction(@NotNull Builder builder) {
    assert builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled != null;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabled = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabled;

    assert builder.myDevicesGetterGetter != null;
    myDevicesGetterGetter = builder.myDevicesGetterGetter;

    assert builder.myGetProperties != null;
    myGetProperties = builder.myGetProperties;

    assert builder.myClock != null;
    myClock = builder.myClock;

    assert builder.myGetSelectedDevices != null;
    myGetSelectedDevices = builder.myGetSelectedDevices;

    assert builder.myGetRunManager != null;
    myGetRunManager = builder.myGetRunManager;

    assert builder.myGetExecutionTargetManager != null;
    myGetExecutionTargetManager = builder.myGetExecutionTargetManager;

    myMultipleDevicesAction = new MultipleDevicesAction(this);
    myModifyDeviceSetAction = new ModifyDeviceSetAction(this);
  }

  @NotNull
  static DeviceAndSnapshotComboBoxAction getInstance() {
    return (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
  }

  boolean areSnapshotsEnabled() {
    return mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get();
  }

  @NotNull
  @VisibleForTesting
  AnAction getMultipleDevicesAction() {
    return myMultipleDevicesAction;
  }

  @NotNull
  @VisibleForTesting
  AnAction getModifyDeviceSetAction() {
    return myModifyDeviceSetAction;
  }

  @NotNull
  List<Device> getDevices(@NotNull Project project) {
    List<Device> devices = myDevicesGetterGetter.apply(project).get();
    devices.sort(new DeviceComparator());

    return devices;
  }

  @Nullable
  @VisibleForTesting
  Device getSelectedDevice(@NotNull Project project) {
    return getSelectedDevice(project, getDevices(project));
  }

  @Nullable
  private Device getSelectedDevice(@NotNull Project project, @NotNull List<Device> devices) {
    if (devices.isEmpty()) {
      return null;
    }

    PropertiesComponent properties = myGetProperties.apply(project);
    String keyAsString = properties.getValue(SELECTED_DEVICE);

    Object key = keyAsString == null ? null : new Key(keyAsString);

    Optional<Device> optionalSelectedDevice = devices.stream()
      .filter(device -> device.getKey().equals(key))
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return devices.get(0);
    }

    Device selectedDevice = optionalSelectedDevice.get();

    Optional<Device> optionalConnectedDevice = devices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (optionalConnectedDevice.isPresent()) {
      Device connectedDevice = optionalConnectedDevice.get();

      Instant connectionTime = connectedDevice.getConnectionTime();
      assert connectionTime != null : "connected device \"" + connectedDevice + "\" has a null connection time";

      if (getSelectionTime(selectedDevice, properties).isBefore(connectionTime)) {
        return connectedDevice;
      }
    }

    return selectedDevice;
  }

  @NotNull
  private static Instant getSelectionTime(@NotNull Device device, @NotNull PropertiesComponent properties) {
    CharSequence time = properties.getValue(SELECTION_TIME);

    if (time == null) {
      // I don't know why this happens
      Logger.getInstance(DeviceAndSnapshotComboBoxAction.class).warn("selected device \"" + device + "\" has a null selection time string");

      return Instant.MIN;
    }

    return Instant.parse(time);
  }

  /**
   * {@link SelectDeviceAction#actionPerformed} calls this when a developer selects a single device with the combo box. Unit tests also call
   * this to simulate the same thing.
   */
  void setSelectedDevice(@NotNull Project project, @NotNull Device selectedDevice) {
    PropertiesComponent properties = myGetProperties.apply(project);
    properties.unsetValue(MULTIPLE_DEVICES_SELECTED);

    properties.setValue(SELECTED_DEVICE, selectedDevice.getKey().toString());
    properties.setValue(SELECTION_TIME, myClock.instant().toString());

    updateExecutionTargetManager(project, new DeviceAndSnapshotComboBoxExecutionTarget(selectedDevice));
  }

  @NotNull
  List<Device> getSelectedDevices(@NotNull Project project) {
    if (isMultipleDevicesSelected(project)) {
      return myGetSelectedDevices.apply(project);
    }

    Device device = getSelectedDevice(project);

    if (device == null) {
      return Collections.emptyList();
    }

    return Collections.singletonList(device);
  }

  private boolean isMultipleDevicesSelected(@NotNull Project project) {
    return myGetProperties.apply(project).getBoolean(MULTIPLE_DEVICES_SELECTED);
  }

  void setMultipleDevicesSelected(@NotNull Project project, @SuppressWarnings("SameParameterValue") boolean multipleDevicesSelected) {
    PropertiesComponent properties = myGetProperties.apply(project);

    properties.unsetValue(SELECTION_TIME);
    properties.unsetValue(SELECTED_DEVICE);

    properties.setValue(MULTIPLE_DEVICES_SELECTED, multipleDevicesSelected);

    updateExecutionTargetManager(project, new DeviceAndSnapshotComboBoxExecutionTarget(myGetSelectedDevices.apply(project)));
  }

  void modifyDeviceSet(@NotNull Project project) {
    if (!isMultipleDevicesSelected(project)) {
      return;
    }

    updateExecutionTargetManager(project, new DeviceAndSnapshotComboBoxExecutionTarget(myGetSelectedDevices.apply(project)));
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createCustomComponent(presentation, JBUI::scale);
  }

  @NotNull
  @VisibleForTesting
  JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull IntUnaryOperator scale) {
    JComponent panel = new JPanel(null);
    GroupLayout layout = new GroupLayout(panel);
    Component button = createComboBoxButton(presentation);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(button, 0, scale.applyAsInt(GroupLayout.DEFAULT_SIZE), scale.applyAsInt(250))
      .addGap(scale.applyAsInt(3));

    Group verticalGroup = layout.createParallelGroup()
      .addComponent(button);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    ComboBoxButton button = new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();
        return new Popup(createPopupActionGroup(this, context), context, runnable);
      }
    };

    button.setName("deviceAndSnapshotComboBoxButton");
    return button;
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();

    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    Collection<AnAction> actions = mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get()
                                   ? newSelectDeviceActionsIncludeSnapshots(project)
                                   : newSelectDeviceActions(project);

    group.addAll(actions);

    if (!actions.isEmpty()) {
      group.addSeparator();
    }

    ActionManager manager = ActionManager.getInstance();

    group.add(myMultipleDevicesAction);
    group.add(myModifyDeviceSetAction);
    group.add(manager.getAction(RunAndroidAvdManagerAction.ID));

    AnAction action = manager.getAction("DeveloperServices.ConnectionAssistant");

    if (action == null) {
      return group;
    }

    group.addSeparator();
    group.add(action);

    return group;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceActions(@NotNull Project project) {
    Map<Boolean, List<Device>> connectednessToDeviceMap = getDevices(project).stream().collect(Collectors.groupingBy(Device::isConnected));

    Collection<Device> connectedDevices = connectednessToDeviceMap.getOrDefault(true, Collections.emptyList());
    Collection<Device> disconnectedDevices = connectednessToDeviceMap.getOrDefault(false, Collections.emptyList());

    boolean connectedDevicesPresent = !connectedDevices.isEmpty();
    Collection<AnAction> actions = new ArrayList<>(connectedDevices.size() + disconnectedDevices.size() + 3);

    if (connectedDevicesPresent) {
      actions.add(new Heading("Running devices"));
    }

    connectedDevices.stream()
      .map(device -> SelectDeviceAction.newSelectDeviceAction(this, project, device))
      .forEach(actions::add);

    boolean disconnectedDevicesPresent = !disconnectedDevices.isEmpty();

    if (connectedDevicesPresent && disconnectedDevicesPresent) {
      actions.add(Separator.create());
    }

    if (disconnectedDevicesPresent) {
      actions.add(new Heading("Available devices"));
    }

    disconnectedDevices.stream()
      .map(device -> SelectDeviceAction.newSelectDeviceAction(this, project, device))
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceActionsIncludeSnapshots(@NotNull Project project) {
    ListMultimap<String, Device> multimap = getDeviceKeyToDeviceMultimap(project);
    Collection<String> deviceKeys = multimap.keySet();
    Collection<AnAction> actions = new ArrayList<>(deviceKeys.size() + 1);

    if (!deviceKeys.isEmpty()) {
      actions.add(new Heading("Available devices"));
    }

    deviceKeys.stream()
      .map(multimap::get)
      .map(devices -> newAction(devices, project))
      .forEach(actions::add);

    return actions;
  }

  @NotNull
  private ListMultimap<String, Device> getDeviceKeyToDeviceMultimap(@NotNull Project project) {
    Collection<Device> devices = myDevicesGetterGetter.apply(project).get();

    // noinspection UnstableApiUsage
    Collector<Device, ?, ListMultimap<String, Device>> collector =
      Multimaps.toMultimap(device -> device.getKey().getDeviceKey(), device -> device, () -> buildListMultimap(devices.size()));

    return devices.stream().collect(collector);
  }

  @NotNull
  private static ListMultimap<String, Device> buildListMultimap(int expectedKeyCount) {
    return MultimapBuilder
      .hashKeys(expectedKeyCount)
      .arrayListValues()
      .build();
  }

  @NotNull
  private AnAction newAction(@NotNull List<Device> devices, @NotNull Project project) {
    if (devices.size() == 1) {
      return SelectDeviceAction.newSelectDeviceAction(this, project, devices.get(0));
    }

    return new SnapshotActionGroup(devices, this, project);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    Presentation presentation = event.getPresentation();

    if (!AndroidUtils.hasAndroidFacets(project)) {
      presentation.setVisible(false);
      return;
    }

    updatePresentation(presentation, myGetRunManager.apply(project).getSelectedConfiguration());

    String place = event.getPlace();

    switch (place) {
      case ActionPlaces.MAIN_MENU:
      case ActionPlaces.ACTION_SEARCH:
        presentation.setIcon(null);
        presentation.setText("Select Device...");

        break;
      case ActionPlaces.MAIN_TOOLBAR:
      case ActionPlaces.NAVIGATION_BAR_TOOLBAR:
        updateInToolbar(presentation, project);
        break;
      default:
        assert false : place;
    }

    updateExecutionTargetManager(project, new DeviceAndSnapshotComboBoxExecutionTarget(getSelectedDevices(project)));
  }

  @VisibleForTesting
  static void updatePresentation(@NotNull Presentation presentation, @Nullable RunnerAndConfigurationSettings settings) {
    if (settings == null) {
      presentation.setDescription("Add a run/debug configuration");
      presentation.setEnabled(false);

      return;
    }

    RunProfile configuration = settings.getConfiguration();

    // Run configurations can explicitly specify they target a local device through DEPLOY_TO_LOCAL_DEVICE.
    if (configuration instanceof UserDataHolder) {
      Boolean deploysToLocalDevice = ((UserDataHolder)configuration).getUserData(DEPLOYS_TO_LOCAL_DEVICE);
      if (deploysToLocalDevice != null && deploysToLocalDevice.booleanValue()) {
        presentation.setDescription(null);
        presentation.setEnabled(true);

        return;
      }
    }

    if (!(configuration instanceof AndroidRunConfiguration || configuration instanceof AndroidTestRunConfiguration)) {
      presentation.setDescription("Not applicable for the \"" + configuration.getName() + "\" configuration");
      presentation.setEnabled(false);

      return;
    }

    presentation.setDescription(null);
    presentation.setEnabled(true);
  }

  private void updateInToolbar(@NotNull Presentation presentation, @NotNull Project project) {
    if (isMultipleDevicesSelected(project)) {
      presentation.setIcon(null);
      presentation.setText("Multiple Devices");

      return;
    }

    List<Device> devices = getDevices(project);

    if (devices.isEmpty()) {
      presentation.setIcon(null);
      presentation.setText("No Devices");

      return;
    }

    Device device = getSelectedDevice(project, devices);
    assert device != null;

    presentation.setIcon(device.getIcon());
    presentation.setText(getText(device, devices, mySelectDeviceSnapshotComboBoxSnapshotsEnabled.get()), false);
  }

  /**
   * Formats the selected device for display in the drop down button. If there's another device with the same name, the text will have the
   * selected device's key appended to it to disambiguate it from the other one. If the SNAPSHOTS_ENABLED flag is on and the device has a
   * nondefault snapshot, the text will have the snapshot's name appended to it. Finally, if the {@link
   * com.android.tools.idea.run.LaunchCompatibilityChecker LaunchCompatibilityChecker} found issues with the device, the text will have the
   * issue appended to it.
   *
   * @param device           the selected device
   * @param devices          the devices to check if any other has the same name as the selected device. The selected device may be in the
   *                         collection.
   * @param snapshotsEnabled the value of the SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED flag. Passed this way for testability.
   */
  @NotNull
  @VisibleForTesting
  static String getText(@NotNull Device device, @NotNull Collection<Device> devices, boolean snapshotsEnabled) {
    boolean anotherDeviceHasSameName = Devices.containsAnotherDeviceWithSameName(devices, device);
    return Devices.getText(device, anotherDeviceHasSameName ? device.getKey() : null, snapshotsEnabled ? device.getSnapshot() : null);
  }

  private void updateExecutionTargetManager(@NotNull Project project, @NotNull ExecutionTarget activeTarget) {
    ExecutionTargetManager executionTargetManager = myGetExecutionTargetManager.apply(project);

    if (executionTargetManager.getActiveTarget().equals(activeTarget)) {
      return;
    }

    // In certain test scenarios, this action may get updated in the main test thread instead of the EDT thread (is this correct?).
    // So we'll just make sure the following gets run on the EDT thread and wait for its result.
    ApplicationManager.getApplication().invokeAndWait(() -> {
      RunManager runManager = myGetRunManager.apply(project);
      RunnerAndConfigurationSettings settings = runManager.getSelectedConfiguration();

      // There is a bug in {@link com.intellij.execution.impl.RunManagerImplKt#clear(boolean)} where it's possible the selected setting's
      // RunConfiguration is be non-existent in the RunManager. This happens when temporary/shared RunnerAndConfigurationSettings are
      // cleared from the list of RunnerAndConfigurationSettings, and the selected RunnerAndConfigurationSettings is temporary/shared and
      // left dangling.
      if (settings == null || runManager.findSettings(settings.getConfiguration()) == null) {
        return;
      }

      executionTargetManager.setActiveTarget(activeTarget);
    });
  }
}
