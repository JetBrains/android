/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SOLUTION_ACCELERATION_NOT_SUPPORTED;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SOLUTION_TURN_OFF_HYPER_V;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.DOWNLOAD_EMULATOR;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.INSTALL_AEHD;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.INSTALL_KVM;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.NONE;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.REINSTALL_AEHD;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.TURNOFF_HYPER_V;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.UPDATE_EMULATOR;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.UPDATE_PLATFORM_TOOLS;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.UPDATE_SYSTEM_IMAGES;

import com.android.SdkConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Error codes returned by "emulator -accel-check".
 * Each error code include a description os what is wrong (problem) and a solution.
 */
public enum AccelerationErrorCode {
  ALREADY_INSTALLED(0, "", NONE, ""),
  NESTED_NOT_SUPPORTED(1, "HAXM is deprecated and should be uninstalled.", NONE, "Please use the latest emulator and follow https://developer.android.com/studio/run/emulator-acceleration#vm-windows to configure WHPX or AEHD instead"),
  INTEL_REQUIRED(2, "HAXM is deprecated and should be uninstalled.", NONE, "Please use the latest emulator and follow https://developer.android.com/studio/run/emulator-acceleration#vm-windows to configure WHPX or AEHD instead"),
  NO_CPU_SUPPORT(3, "Your CPU does not support required features (VT-x or SVM).", NONE, SOLUTION_ACCELERATION_NOT_SUPPORTED),
  NO_CPU_VTX_SUPPORT(4, "Your CPU does not support VT-x.", NONE, SOLUTION_ACCELERATION_NOT_SUPPORTED),
  NO_CPU_NX_SUPPORT(5, "Your CPU does not support NX.", NONE, SOLUTION_ACCELERATION_NOT_SUPPORTED),
  ACCELERATION_NOT_INSTALLED_LINUX(6, "KVM is not installed.", INSTALL_KVM, "Enable Linux KVM for better emulation performance."),
  ACCELERATION_NOT_INSTALLED_WIN(6, "Android Emulator hypervisor driver is not installed.", INSTALL_AEHD, "Install Android Emulator hypervisor driver for better emulation performance."),
  ACCELERATION_OBSOLETE(7, "Virtual machine acceleration driver is out-of-date.", REINSTALL_AEHD, "Reinstall Android Emulator hypervisor driver."),
  DEV_NOT_FOUND_LINUX(8, "/dev/kvm is not found.", NONE, "Enable VT-x in your BIOS security settings, ensure that your Linux distro has working KVM module."),
  DEV_NOT_FOUND_WIN(8, "Android Emulator hypervisor driver device is not found.", NONE, "Enable VT-x in your BIOS security settings, ensure that Android Emulator hypervisor driver is installed properly. Try disabling 3rd party security software if the problem still occurs."),
  VT_DISABLED(9, "VT-x is disabled in BIOS.", NONE, "Enable VT-x in your BIOS security settings (refer to documentation for your computer)."),
  NX_DISABLED(10, "NX is disabled in BIOS.", NONE, "Enable NX in your BIOS settings (refer to documentation for your computer)."),
  DEV_PERMISSION_LINUX(11, "/dev/kvm device: permission denied.", NONE, "Grant current user access to /dev/kvm"),
  DEV_PERMISSION_WIN(11, "Android Emulator hypervisor driver device: permission denied.", REINSTALL_AEHD, "Reinstall Android Emulator hypervisor driver."),
  DEV_OPEN_FAILED_LINUX(12, "/dev/kvm device: open failed.", NONE, "Grant current user access to /dev/kvm"),
  DEV_OPEN_FAILED_WIN(12, "Android Emulator hypervisor driver device: open failed.", REINSTALL_AEHD, "Reinstall Android Emulator hypervisor driver."),
  DEV_IOCTL_FAILED_LINUX(13, "/dev/kvm device: ioctl denied.", NONE, "Upgrade your kernel."),
  DEV_IOCTL_FAILED_WIN(13, "Android Emulator hypervisor driver device: ioctl denied.", REINSTALL_AEHD, "Reinstall Android Emulator hypervisor driver."),
  DEV_OBSOLETE_LINUX(14, "KVM module is too old.", NONE, "Upgrade your kernel."),
  DEV_OBSOLETE_WIN(14, "Virtual machine acceleration driver out-of-date.", REINSTALL_AEHD, "Reinstall Android Emulator hypervisor driver."),
  HYPER_V_ENABLED(15, "Android Emulator is incompatible with Hyper-V.", TURNOFF_HYPER_V, SOLUTION_TURN_OFF_HYPER_V),
  EMULATOR_ERROR(138, "Accelerator Detection Problem.", NONE, "Please file a bug against Android Studio."),
  UNKNOWN_ERROR(-1, "Unknown Error", NONE, "Please file a bug against Android Studio."),
  NO_EMULATOR_INSTALLED(-2, "No emulator installed", DOWNLOAD_EMULATOR, "Please download the emulator"),
  TOOLS_UPDATE_REQUIRED(-3, "Emulator is outdated", UPDATE_EMULATOR, "Please download the newest tools"),
  NOT_ENOUGH_MEMORY(-4, "Not enough memory to run the AVD", NONE, "Get more available memory for the AVD"),
  HAXM_REQUIRES_WINDOWS(-5, "HAXM is deprecated.", NONE, "Please file a bug against Android Studio."),
  PLATFORM_TOOLS_UPDATE_ADVISED(-6, "Platform tools update is available", UPDATE_PLATFORM_TOOLS, "Please download platform tools"),
  SYSTEM_IMAGE_UPDATE_ADVISED(-7, "System image update is available", UPDATE_SYSTEM_IMAGES, "Please update system images"),
  HAXM_REQUIRES_INTEL_CPU(-8, "HAXM is deprecated and should be uninstalled.", NONE, "Please use the latest emulator and follow https://developer.android.com/studio/run/emulator-acceleration#vm-windows to configure WHPX or AEHD instead"),
  AEHD_REQUIRES_WINDOWS(-9, "Android Emulator hypervisor driver can only be installed on Windows.", NONE, "Please file a bug against Android Studio.");

  private int myErrorCode;
  private String myProblem;
  private SolutionCode mySolution;
  private String mySolutionMessage;

  AccelerationErrorCode(int code, @NotNull String problem, @NotNull SolutionCode solution, @NotNull String solutionMessage) {
    myErrorCode = code;
    myProblem = problem;
    mySolution = solution;
    mySolutionMessage = solutionMessage;
  }

  public int getErrorCode() {
    return myErrorCode;
  }

  public String getProblem() {
    return myProblem;
  }

  void setProblem(String problem) {
    myProblem = problem;
  }

  public SolutionCode getSolution() {
    return mySolution;
  }

  public String getSolutionMessage() {
    return mySolutionMessage;
  }

  public static AccelerationErrorCode fromExitCode(int code) {
    switch (code) {
      case  0: return ALREADY_INSTALLED;
      case  3: return NO_CPU_SUPPORT;
      case  4: return NO_CPU_VTX_SUPPORT;
      case  5: return NO_CPU_NX_SUPPORT;
      case  6: return SystemInfo.isLinux ? ACCELERATION_NOT_INSTALLED_LINUX :
                      (SystemInfo.isWindows ? ACCELERATION_NOT_INSTALLED_WIN :
                       UNKNOWN_ERROR);
      case  8: return SystemInfo.isLinux ? DEV_NOT_FOUND_LINUX :
                      (SystemInfo.isWindows ? DEV_NOT_FOUND_WIN :
                       UNKNOWN_ERROR);
      case  9: return VT_DISABLED;
      case 10: return NX_DISABLED;
      case 11: return SystemInfo.isLinux ? DEV_PERMISSION_LINUX :
                      (SystemInfo.isWindows ? DEV_PERMISSION_WIN :
                       UNKNOWN_ERROR);
      case 12: return SystemInfo.isLinux ? DEV_OPEN_FAILED_LINUX :
                      (SystemInfo.isWindows ? DEV_OPEN_FAILED_WIN :
                       UNKNOWN_ERROR);
      case 13: return SystemInfo.isLinux ? DEV_IOCTL_FAILED_LINUX :
                      (SystemInfo.isWindows ? DEV_IOCTL_FAILED_WIN :
                       UNKNOWN_ERROR);
      case 14: return SystemInfo.isLinux ? DEV_OBSOLETE_LINUX :
                      (SystemInfo.isWindows ? DEV_OBSOLETE_WIN :
                       UNKNOWN_ERROR);
      case 15: return HYPER_V_ENABLED;
      default:
        Logger.getInstance(AccelerationErrorCode.class).warn(SdkConstants.FN_EMULATOR_CHECK + " terminated with code " + code);
        return UNKNOWN_ERROR;
    }
  }
}
