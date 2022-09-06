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
import com.intellij.execution.RunManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  /**
   * Run configurations that aren't {@link AndroidRunConfiguration} or {@link AndroidTestRunConfiguration} can use this key
   * to express their applicability for DeviceAndSnapshotComboBoxAction by setting it to true in their user data.
   */
  public static final com.intellij.openapi.util.Key<Boolean> DEPLOYS_TO_LOCAL_DEVICE =
    com.intellij.openapi.util.Key.create("DeviceAndSnapshotComboBoxAction.deploysToLocalDevice");

  private final @NotNull BooleanSupplier mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet;

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @NotNull
  private final Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

  private final @NotNull BiFunction<Project, List<Device>, DialogWrapper> myNewSelectMultipleDevicesDialog;

  @NotNull
  private final Function<Project, RunManager> myGetRunManager;
  private final @NotNull UpdatableDeviceHelpTooltip myUpdatableDeviceHelpTooltip;

  @VisibleForTesting
  static final class Builder {
    private @Nullable BooleanSupplier mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet;

    @Nullable
    private Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

    @Nullable
    private Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

    @Nullable
    private Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

    private @Nullable BiFunction<Project, List<Device>, DialogWrapper> myNewSelectMultipleDevicesDialog;

    @Nullable
    private Function<Project, RunManager> myGetRunManager;

    Builder() {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet = () -> false;
      myDevicesGetterGetter = project -> null;
      myDevicesSelectedServiceGetInstance = project -> null;
      myExecutionTargetServiceGetInstance = project -> null;
      myNewSelectMultipleDevicesDialog = (project, devices) -> null;
      myGetRunManager = project -> null;
    }

    @NotNull Builder setSelectDeviceSnapshotComboBoxSnapshotsEnabledGet(@NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet) {
      mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet = selectDeviceSnapshotComboBoxSnapshotsEnabledGet;
      return this;
    }

    @NotNull
    Builder setDevicesGetterGetter(@NotNull Function<Project, AsyncDevicesGetter> devicesGetterGetter) {
      myDevicesGetterGetter = devicesGetterGetter;
      return this;
    }

    @NotNull
    Builder setDevicesSelectedServiceGetInstance(@NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
      myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;
      return this;
    }

    @NotNull
    Builder setExecutionTargetServiceGetInstance(@NotNull Function<Project, ExecutionTargetService> executionTargetServiceGetInstance) {
      myExecutionTargetServiceGetInstance = executionTargetServiceGetInstance;
      return this;
    }

    @NotNull Builder setNewSelectMultipleDevicesDialog(
      @NotNull BiFunction<Project, List<Device>, DialogWrapper> newSelectMultipleDevicesDialog) {
      myNewSelectMultipleDevicesDialog = newSelectMultipleDevicesDialog;
      return this;
    }

    @NotNull
    Builder setGetRunManager(@NotNull Function<Project, RunManager> getRunManager) {
      myGetRunManager = getRunManager;
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
           .setSelectDeviceSnapshotComboBoxSnapshotsEnabledGet(StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED::get)
           .setDevicesGetterGetter(AsyncDevicesGetter::getInstance)
           .setDevicesSelectedServiceGetInstance(DevicesSelectedService::getInstance)
           .setExecutionTargetServiceGetInstance(ExecutionTargetService::getInstance)
           .setNewSelectMultipleDevicesDialog(SelectMultipleDevicesDialog::new)
           .setGetRunManager(RunManager::getInstance));
  }

  @NonInjectable
  private DeviceAndSnapshotComboBoxAction(@NotNull Builder builder) {
    assert builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet != null;
    mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet = builder.mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet;

    assert builder.myDevicesGetterGetter != null;
    myDevicesGetterGetter = builder.myDevicesGetterGetter;

    assert builder.myDevicesSelectedServiceGetInstance != null;
    myDevicesSelectedServiceGetInstance = builder.myDevicesSelectedServiceGetInstance;

    assert builder.myExecutionTargetServiceGetInstance != null;
    myExecutionTargetServiceGetInstance = builder.myExecutionTargetServiceGetInstance;

    assert builder.myNewSelectMultipleDevicesDialog != null;
    myNewSelectMultipleDevicesDialog = builder.myNewSelectMultipleDevicesDialog;

    assert builder.myGetRunManager != null;
    myGetRunManager = builder.myGetRunManager;

    myUpdatableDeviceHelpTooltip = new UpdatableDeviceHelpTooltip();
  }

  @NotNull
  static DeviceAndSnapshotComboBoxAction getInstance() {
    // noinspection CastToConcreteClass
    return (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
  }

  boolean areSnapshotsEnabled() {
    return mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet.getAsBoolean();
  }

  @NotNull
  Optional<List<Device>> getDevices(@NotNull Project project) {
    Optional<List<Device>> optionalDevices = myDevicesGetterGetter.apply(project).get();

    if (optionalDevices.isPresent()) {
      List<Device> devices = optionalDevices.get();
      devices.sort(new DeviceComparator());

      return Optional.of(devices);
    }

    return optionalDevices;
  }

  void setTargetSelectedWithComboBox(@NotNull Project project, @NotNull Target target) {
    myDevicesSelectedServiceGetInstance.apply(project).setTargetSelectedWithComboBox(target);
    setActiveExecutionTarget(project, Collections.singleton(target));
  }

  public boolean isMultipleTargetsSelectedInComboBox(@NotNull Project project) {
    return myDevicesSelectedServiceGetInstance.apply(project).isMultipleDevicesSelectedInComboBox();
  }

  public int getNumberOfSelectedDevices(@NotNull Project project) {
    return getSelectedDevices(project).size();
  }

  @NotNull List<Device> getSelectedDevices(@NotNull Project project) {
    List<Device> devices = getDevices(project).orElse(Collections.emptyList());
    return Target.filterDevices(getSelectedTargets(project, devices), devices);
  }

  @NotNull Set<Target> getSelectedTargets(@NotNull Project project) {
    return getSelectedTargets(project, getDevices(project).orElse(Collections.emptyList()));
  }

  @NotNull Set<Target> getSelectedTargets(@NotNull Project project, @NotNull List<Device> devices) {
    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);

    if (service.isMultipleDevicesSelectedInComboBox()) {
      return service.getTargetsSelectedWithDialog(devices);
    }

    return service.getTargetSelectedWithComboBox(devices).map(Collections::singleton).orElseGet(Collections::emptySet);
  }

  void selectMultipleDevices(@NotNull Project project) {
    List<Device> devices = myDevicesGetterGetter.apply(project).get().orElseThrow(AssertionError::new);

    if (!myNewSelectMultipleDevicesDialog.apply(project, devices).showAndGet()) {
      return;
    }

    DevicesSelectedService service = myDevicesSelectedServiceGetInstance.apply(project);
    service.setMultipleDevicesSelectedInComboBox(!service.getTargetsSelectedWithDialog(devices).isEmpty());

    setActiveExecutionTarget(project, getSelectedTargets(project, devices));
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
      .addComponent(button, 0, GroupLayout.DEFAULT_SIZE, scale.applyAsInt(250))
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
      @NotNull
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();
        return new Popup(createPopupActionGroup(this, context), context, runnable);
      }
    };

    myUpdatableDeviceHelpTooltip.installOn(button);
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
    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    return new PopupActionGroup(getDevices(project).orElseThrow(AssertionError::new), this);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();

    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    Optional<List<Device>> optionalDevices = getDevices(project);

    if (!optionalDevices.isPresent()) {
      presentation.setEnabled(false);
      presentation.setText("Loading Devices...");

      return;
    }

    List<Device> devices = optionalDevices.get();

    Updater updater = new Updater.Builder()
      .setProject(project)
      .setPresentation(presentation)
      .setPlace(event.getPlace())
      .setDevicesSelectedService(myDevicesSelectedServiceGetInstance.apply(project))
      .setDevices(devices)
      .setConfigurationAndSettings(myGetRunManager.apply(project).getSelectedConfiguration())
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabledGet(mySelectDeviceSnapshotComboBoxSnapshotsEnabledGet)
      .build();

    updater.update();
    updateTooltip(project);
    if (presentation.isVisible()) {
      setActiveExecutionTarget(project, getSelectedTargets(project, devices));
    }
  }

  private void updateTooltip(@NotNull Project project) {
    List<Device> selectedDevices = getSelectedDevices(project);
    if (selectedDevices.size() == 1) {
      myUpdatableDeviceHelpTooltip.updateTooltip(selectedDevices.get(0));
    }
    else {
      myUpdatableDeviceHelpTooltip.cancel();
    }
  }

  private void setActiveExecutionTarget(@NotNull Project project, @NotNull Set<Target> targets) {
    AsyncDevicesGetter getter = myDevicesGetterGetter.apply(project);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(targets, getter));
  }
}
