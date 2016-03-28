/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class AvdComboBox extends ComboboxWithBrowseButton {
  private final boolean myAddEmptyElement;
  private final boolean myShowNotLaunchedOnly;
  private final Alarm myAlarm = new Alarm(this);
  private IdDisplay[] myOldAvds = new IdDisplay[0];
  private final Project myProject;

  public AvdComboBox(@Nullable Project project, boolean addEmptyElement, boolean showNotLaunchedOnly) {
    myProject = project;
    myAddEmptyElement = addEmptyElement;
    myShowNotLaunchedOnly = showNotLaunchedOnly;

    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final AndroidPlatform platform = findAndroidPlatform();
        AvdComboBox avdComboBox = AvdComboBox.this;
        if (platform == null) {
          Messages.showErrorDialog(avdComboBox, "Cannot find any configured Android SDK");
          return;
        }
        RunAndroidAvdManagerAction action = new RunAndroidAvdManagerAction();
        action.openAvdManager(myProject);
        AvdInfo selected = action.getSelected();
        if (selected != null) {
          getComboBox().setSelectedItem(IdDisplay.create(selected.getName(), ""));
        }
      }
    });

    setMinimumSize(new Dimension(JBUI.scale(100), getMinimumSize().height));
  }


  public void startUpdatingAvds(@NotNull ModalityState modalityState) {
    if (!getComboBox().isPopupVisible()) {
      doUpdateAvds();
    }
    addUpdatingRequest(modalityState);
  }

  private void addUpdatingRequest(@NotNull final ModalityState modalityState) {
    if (myAlarm.isDisposed()) {
      return;
    }
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        startUpdatingAvds(modalityState);
      }
    }, 500, modalityState);
  }

  @Override
  public void dispose() {
    myAlarm.cancelAllRequests();
    super.dispose();
  }

  private void doUpdateAvds() {
    final Module module = getModule();
    if (module == null || module.isDisposed()) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    final IdDisplay[] newAvds;

    if (facet != null) {
      final Set<String> filteringSet = new HashSet<String>();
      if (myShowNotLaunchedOnly) {
        final AndroidDebugBridge debugBridge = AndroidSdkUtils.getDebugBridge(facet.getModule().getProject());
        if (debugBridge != null) {
          for (IDevice device : debugBridge.getDevices()) {
            final String avdName = device.getAvdName();
            if (avdName != null && avdName.length() > 0) {
              filteringSet.add(avdName);
            }
          }
        }
      }

      final List<IdDisplay> newAvdList = new ArrayList<IdDisplay>();
      if (myAddEmptyElement) {
        newAvdList.add(IdDisplay.create("", ""));
      }
      for (AvdInfo avd : facet.getAllAvds()) {
        String displayName = avd.getProperties().get(AvdManagerConnection.AVD_INI_DISPLAY_NAME);
        final String avdName = displayName == null || displayName.isEmpty() ? avd.getName() : displayName;
        if (!filteringSet.contains(avdName)) {
          newAvdList.add(IdDisplay.create(avd.getName(), avdName));
        }
      }

      newAvds = ArrayUtil.toObjectArray(newAvdList, IdDisplay.class);
    }
    else {
      newAvds = new IdDisplay[0];
    }

    if (!Arrays.equals(myOldAvds, newAvds)) {
      myOldAvds = newAvds;
      final Object selected = getComboBox().getSelectedItem();
      getComboBox().setModel(new DefaultComboBoxModel(newAvds));
      getComboBox().setSelectedItem(selected);
    }
  }

  @Nullable
  public abstract Module getModule();

  @Nullable
  private AndroidPlatform findAndroidPlatform() {
    AndroidPlatform platform = findAndroidPlatformFromModule();
    if (platform != null) {
       return platform;
    }

    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      platform = AndroidPlatform.getInstance(sdk);
      if (platform != null) {
        return platform;
      }
    }
    return null;
  }

  @Nullable
  private AndroidPlatform findAndroidPlatformFromModule() {
    Module module = getModule();
    return module != null ? AndroidPlatform.getInstance(module) : null;
  }
}
