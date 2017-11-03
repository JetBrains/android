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

import com.android.ddmlib.IDevice;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Predicate;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeviceChooserDialog extends DialogWrapper {
  private final DeviceChooser myDeviceChooser;

  public DeviceChooserDialog(@NotNull AndroidFacet facet,
                             @NotNull IAndroidTarget projectTarget,
                             boolean multipleSelection,
                             @Nullable String[] selectedSerials,
                             @Nullable Predicate<IDevice> filter) {
    super(facet.getModule().getProject(), true);
    setTitle(AndroidBundle.message("choose.device.dialog.title"));

    getOKAction().setEnabled(false);

    myDeviceChooser = new DeviceChooser(multipleSelection, getOKAction(), facet, projectTarget, filter);
    Disposer.register(myDisposable, myDeviceChooser);
    myDeviceChooser.addListener(this::updateOkButton);

    init();
    myDeviceChooser.init(selectedSerials);
  }

  private void updateOkButton() {
    IDevice[] devices = getSelectedDevices();
    boolean enabled = devices.length > 0;
    for (IDevice device : devices) {
      if (!device.isOnline()) {
        enabled = false;
      }
    }
    getOKAction().setEnabled(enabled);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceChooser.getPreferredFocusComponent();
  }

  @Override
  protected void doOKAction() {
    myDeviceChooser.finish();
    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidDeviceChooserDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myDeviceChooser.getPanel();
  }

  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }
}
