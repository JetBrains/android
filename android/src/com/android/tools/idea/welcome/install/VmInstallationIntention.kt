/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

/**
 * Specifies what to do with the virtualization package.
 *
 * For most packages managed by the SDK manager, "installation" means simply unpacking the packages
 * into the appropriate directory beneath the SDK root. For the virtualization packages, however,
 * the contents of the package are only installation (and uninstallation) scripts that install the
 * VM software in the operating system.
 *
 * Thus, installation has two phases: unpacking the packages (which we call "installation", and
 * running the setup script (which we call "configuration").
 *
 * the virtualization packages must be installed into the operating system
 *
 * Installation of the VM packages has two phases: first, the
 *
 * The VM needs to be installed and configured. (Note that "install" here means unpacking the
 * package files into their installation directory below the SDK root, and "configure" means running
 * the installation script that installs the components in the OS.)
 */
enum class VmInstallationIntention {
  /**
   * Install and configure. If it is already installed, it will be updated if there is a newer
   * version.
   */
  INSTALL_WITH_UPDATES,
  /** Install and configure. If it is already installed, do nothing. */
  INSTALL_WITHOUT_UPDATES,
  /** Only configure; the package should already be installed. */
  CONFIGURE_ONLY,
  /**
   * Run the uninstallation script to remove it from the operating system, and remove the package.
   */
  UNINSTALL;

  fun isInstall(): Boolean = this == INSTALL_WITHOUT_UPDATES || this == INSTALL_WITH_UPDATES
}
