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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.RunManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Key;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtil.FontSize;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  /**
   * The key for the LaunchCompatibility presentation client property
   */
  static final Key<LaunchCompatibility> LAUNCH_COMPATIBILITY_KEY = new Key<>("DeviceAndSnapshotComboBoxAction.launchCompatibility");

  @NotNull
  private final Function<Project, AsyncDevicesGetter> myDevicesGetterGetter;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @NotNull
  private final Function<Project, ExecutionTargetService> myExecutionTargetServiceGetInstance;

  private final @NotNull BiFunction<Project, List<Device>, DialogWrapper> myNewSelectMultipleDevicesDialog;

  @NotNull
  private final Function<Project, RunManager> myGetRunManager;

  @VisibleForTesting
  static final class Builder {
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
      myDevicesGetterGetter = project -> null;
      myDevicesSelectedServiceGetInstance = project -> null;
      myExecutionTargetServiceGetInstance = project -> null;
      myNewSelectMultipleDevicesDialog = (project, devices) -> null;
      myGetRunManager = project -> null;
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

    @NotNull
    @VisibleForTesting
    Builder setNewSelectMultipleDevicesDialog(@NotNull BiFunction<Project, List<Device>, DialogWrapper> newSelectMultipleDevicesDialog) {
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
           .setDevicesGetterGetter(AsyncDevicesGetter::getInstance)
           .setDevicesSelectedServiceGetInstance(DevicesSelectedService::getInstance)
           .setExecutionTargetServiceGetInstance(ExecutionTargetService::getInstance)
           .setNewSelectMultipleDevicesDialog(SelectMultipleDevicesDialog::new)
           .setGetRunManager(RunManager::getInstance));
  }

  @NonInjectable
  private DeviceAndSnapshotComboBoxAction(@NotNull Builder builder) {
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
  }

  @NotNull
  static DeviceAndSnapshotComboBoxAction getInstance() {
    // noinspection CastToConcreteClass
    return (DeviceAndSnapshotComboBoxAction)ActionManager.getInstance().getAction("DeviceAndSnapshotComboBox");
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

  @NotNull
  List<Device> getSelectedDevices(@NotNull Project project) {
    List<Device> devices = getDevices(project).orElse(Collections.emptyList());
    return Target.filterDevices(getSelectedTargets(project, devices), devices);
  }

  @NotNull
  Optional<Set<Target>> getSelectedTargets(@NotNull Project project) {
    return getDevices(project).map(devices -> getSelectedTargets(project, devices));
  }

  @NotNull
  Set<Target> getSelectedTargets(@NotNull Project project, @NotNull List<Device> devices) {
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

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(button)
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    panel.setOpaque(false);

    return panel;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    return new DeviceAndSnapshotComboBoxButton(presentation);
  }

  private final class DeviceAndSnapshotComboBoxButton extends ComboBoxButton {
    private DeviceAndSnapshotComboBoxButton(@NotNull Presentation presentation) {
      super(presentation);
      setName("deviceAndSnapshotComboBoxButton");
    }

    @Override
    protected void presentationChanged(@NotNull PropertyChangeEvent event) {
      super.presentationChanged(event);
      var name = event.getPropertyName();

      if (!Objects.equals(name, LAUNCH_COMPATIBILITY_KEY.toString())) {
        return;
      }

      HelpTooltip.dispose(this);
      var value = event.getNewValue();

      if (value == null) {
        return;
      }

      var tooltip = new HelpTooltip();

      if (!TooltipsKt.updateTooltip((LaunchCompatibility)value, tooltip)) {
        return;
      }

      tooltip.installOn(this);
    }

    @NotNull
    @Override
    protected JBPopup createPopup(@Nullable Runnable runnable) {
      var context = getDataContext();
      assert runnable != null;

      return new Popup(createPopupActionGroup(this, context), context, runnable);
    }

    @NotNull
    @Override
    public Font getFont() {
      // noinspection UnstableApiUsage
      return ExperimentalUI.isNewUI() ? UIUtil.getLabelFont(FontSize.NORMAL) : super.getFont();
    }
  }

  @Override
  protected boolean shouldShowDisabledActions() {
    return true;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    assert project != null;

    return new PopupActionGroup(getDevices(project).orElseThrow(AssertionError::new), this);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getProject();

    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    if (!AndroidUtils.hasAndroidFacets(project)) {
      presentation.setVisible(false);
      return;
    }

    Optional<List<Device>> optionalDevices = getDevices(project);

    if (optionalDevices.isEmpty()) {
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
      .build();

    updater.update();

    event.getUpdateSession().compute(this, "Set active device", ActionUpdateThread.EDT, () -> {
      if (presentation.isVisible()) {
        setActiveExecutionTarget(project, getSelectedTargets(project, devices));
      }
      return null;
    });
  }

  private void setActiveExecutionTarget(@NotNull Project project, @NotNull Set<Target> targets) {
    AsyncDevicesGetter getter = myDevicesGetterGetter.apply(project);
    myExecutionTargetServiceGetInstance.apply(project).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(targets, getter));
  }
}
