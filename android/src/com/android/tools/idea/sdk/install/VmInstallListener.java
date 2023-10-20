/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install;

import com.android.annotations.NonNull;
import com.android.repository.api.Installer;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.Uninstaller;
import com.android.tools.idea.sdk.wizard.VmWizard;
import com.android.tools.idea.welcome.install.VmInstallationIntention;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link PackageOperation.StatusChangeListener} for installing VM. Runs the {@link VmWizard} instead of the normal quickfix wizard.
 */
public class VmInstallListener implements PackageOperation.StatusChangeListener {
  @NonNull private VmType myType;

  public VmInstallListener(@NonNull VmType type) {
    myType = type;
  }

  @Override
  public void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress)
    throws PackageOperation.StatusChangeListenerException {

    if ((op instanceof Uninstaller && op.getInstallStatus() == PackageOperation.InstallStatus.RUNNING) ||
        (op instanceof Installer && op.getInstallStatus() == PackageOperation.InstallStatus.COMPLETE)) {
      // There are two possible workflows:
      // 1) Installation workflow: Install SDK package -> invoke wizard to run installer
      // 2) Uninstallation workflow: Invoke wizard to run installer with uninstallation params -> Uninstall SDK package
      // In both cases we need to leave the state of the SDK package consistent with the installer invocation success
      // status.
      // So if calling the installer during uninstallation fails, we simply throw an exception here and do not proceed
      // with SDK package removal, as the SDK package operation is in PREPARING state
      // If calling the installer during installation fails, then it is the responsibility of the wizard to cleanup the SDK
      // package as well

      final AtomicBoolean result = new AtomicBoolean(false);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        // Either we just installed the package and we need to "configure" it (run the installation script),
        // or we're about to uninstall it.
        VmWizard wizard =
          new VmWizard(op instanceof Uninstaller ? VmInstallationIntention.UNINSTALL : VmInstallationIntention.CONFIGURE_ONLY, myType);
        wizard.init();
        result.set(wizard.showAndGet());
      }, ModalityState.any());
      if (!result.get()) {
        throw new PackageOperation.StatusChangeListenerException(myType + " setup failed!");
      }
    }
  }
}
