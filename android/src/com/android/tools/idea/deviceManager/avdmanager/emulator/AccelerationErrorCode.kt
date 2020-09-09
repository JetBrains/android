/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.emulator

import com.android.tools.idea.welcome.install.CpuVendor.isAMD
import com.intellij.openapi.util.SystemInfo

/**
 * Error codes returned by "emulator -accel-check".
 * Each error code include a description os what is wrong (problem) and a solution.
 */
enum class AccelerationErrorCode(
  val errorCode: Int,
  var problem: String,
  val solution: AccelerationErrorSolution.SolutionCode,
  val solutionMessage: String
) {
  ALREADY_INSTALLED(0, "", AccelerationErrorSolution.SolutionCode.NONE, ""),
  NESTED_NOT_SUPPORTED(1, "HAXM doesn't support nested virtual machines.", AccelerationErrorSolution.SolutionCode.NONE, AccelerationErrorSolution.SOLUTION_NESTED_VIRTUAL_MACHINE),
  INTEL_REQUIRED(2, "HAXM requires a Genuine Intel processor.", AccelerationErrorSolution.SolutionCode.NONE, AccelerationErrorSolution.SOLUTION_ACCELERATION_NOT_SUPPORTED),
  NO_CPU_SUPPORT(3, "Your CPU does not support required features (VT-x or SVM).", AccelerationErrorSolution.SolutionCode.NONE, AccelerationErrorSolution.SOLUTION_ACCELERATION_NOT_SUPPORTED),
  NO_CPU_VTX_SUPPORT(4, "Your CPU does not support VT-x.", AccelerationErrorSolution.SolutionCode.NONE, AccelerationErrorSolution.SOLUTION_ACCELERATION_NOT_SUPPORTED),
  NO_CPU_NX_SUPPORT(5, "Your CPU does not support NX.", AccelerationErrorSolution.SolutionCode.NONE, AccelerationErrorSolution.SOLUTION_ACCELERATION_NOT_SUPPORTED),
  ACCELERATION_NOT_INSTALLED_LINUX(6, "KVM is not installed.", AccelerationErrorSolution.SolutionCode.INSTALL_KVM, "Enable Linux KVM for better emulation performance."),
  ACCELERATION_NOT_INSTALLED_WIN_MAC_INTEL(6, "HAXM is not installed.", AccelerationErrorSolution.SolutionCode.INSTALL_HAXM, "Install Intel HAXM for better emulation performance."),
  ACCELERATION_NOT_INSTALLED_WIN_AMD(6, "Android Emulator Hypervisor Driver for AMD Processors is not installed.", AccelerationErrorSolution.SolutionCode.INSTALL_GVM, "Install Android Emulator Hypervisor Driver for AMD Processors for better emulation performance."),
  ACCELERATION_OBSOLETE(7, "Virtual machine acceleration driver is out-of-date.", AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM, "A newer HAXM Version is required. Please update."),
  DEV_NOT_FOUND_LINUX(8, "/dev/kvm is not found.", AccelerationErrorSolution.SolutionCode.NONE, "Enable VT-x in your BIOS security settings, ensure that your Linux distro has working KVM module."),
  DEV_NOT_FOUND_WIN_MAC_INTEL(8, "HAXM device is not found.", AccelerationErrorSolution.SolutionCode.NONE, "Enable VT-x in your BIOS security settings, ensure that HAXM is installed properly. Try disabling 3rd party security software if the problem still occurs."),
  DEV_NOT_FOUND_WIN_AMD(8, "Android Emulator Hypervisor Driver for AMD Processors device is not found.", AccelerationErrorSolution.SolutionCode.NONE, "Enable VT-x in your BIOS security settings, ensure that Android Emulator Hypervisor Driver for AMD Processors is installed properly. Try disabling 3rd party security software if the problem still occurs."),
  VT_DISABLED(9, "VT-x is disabled in BIOS.", AccelerationErrorSolution.SolutionCode.NONE, "Enable VT-x in your BIOS security settings (refer to documentation for your computer)."),
  NX_DISABLED(10, "NX is disabled in BIOS.", AccelerationErrorSolution.SolutionCode.NONE, "Enable NX in your BIOS settings (refer to documentation for your computer)."),
  DEV_PERMISSION_LINUX(11, "/dev/kvm device: permission denied.", AccelerationErrorSolution.SolutionCode.NONE, "Grant current user access to /dev/kvm"),
  DEV_PERMISSION_WIN_MAC_INTEL(11, "HAXM device: permission denied.", AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM, "Reinstall HAXM."),
  DEV_PERMISSION_WIN_AMD(11, "Android Emulator Hypervisor Driver for AMD Processors device: permission denied.", AccelerationErrorSolution.SolutionCode.REINSTALL_GVM, "Reinstall Android Emulator Hypervisor Driver for AMD Processors."),
  DEV_OPEN_FAILED_LINUX(12, "/dev/kvm device: open failed.", AccelerationErrorSolution.SolutionCode.NONE, "Grant current user access to /dev/kvm"),
  DEV_OPEN_FAILED_WIN_MAC_INTEL(12, "HAXM device: open failed.", AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM, "Reinstall HAXM."),
  DEV_OPEN_FAILED_WIN_AMD(12, "Android Emulator Hypervisor Driver for AMD Processors device: open failed.", AccelerationErrorSolution.SolutionCode.REINSTALL_GVM, "Reinstall Android Emulator Hypervisor Driver for AMD Processors."),
  DEV_IOCTL_FAILED_LINUX(13, "/dev/kvm device: ioctl denied.", AccelerationErrorSolution.SolutionCode.NONE, "Upgrade your kernel."),
  DEV_IOCTL_FAILED_WIN_MAC_INTEL(13, "HAXM device: ioctl denied.", AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM, "Reinstall HAXM."),
  DEV_IOCTL_FAILED_WIN_AMD(13, "Android Emulator Hypervisor Driver for AMD Processors device: ioctl denied.", AccelerationErrorSolution.SolutionCode.REINSTALL_GVM, "Reinstall Android Emulator Hypervisor Driver for AMD Processors."),
  DEV_OBSOLETE_LINUX(14, "KVM module is too old.", AccelerationErrorSolution.SolutionCode.NONE, "Upgrade your kernel."),
  DEV_OBSOLETE_WIN_MAC_INTEL(14, "Virtual machine acceleration driver out-of-date.", AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM, "Reinstall HAXM."),
  DEV_OBSOLETE_WIN_AMD(14, "Virtual machine acceleration driver out-of-date.", AccelerationErrorSolution.SolutionCode.REINSTALL_GVM, "Reinstall Android Emulator Hypervisor Driver for AMD Processors."),
  HYPER_V_ENABLED(15, "Android Emulator is incompatible with Hyper-V.", AccelerationErrorSolution.SolutionCode.TURNOFF_HYPER_V, AccelerationErrorSolution.SOLUTION_TURN_OFF_HYPER_V),
  EMULATOR_ERROR(138, "Accelerator Detection Problem.", AccelerationErrorSolution.SolutionCode.NONE, "Please file a bug against Android Studio."),
  UNKNOWN_ERROR(-1, "Unknown Error", AccelerationErrorSolution.SolutionCode.NONE, "Please file a bug against Android Studio."),
  NO_EMULATOR_INSTALLED(-2, "No emulator installed", AccelerationErrorSolution.SolutionCode.DOWNLOAD_EMULATOR, "Please download the emulator"),
  TOOLS_UPDATE_REQUIRED(-3, "Emulator is outdated", AccelerationErrorSolution.SolutionCode.UPDATE_EMULATOR, "Please download the newest tools"),
  NOT_ENOUGH_MEMORY(-4, "Not enough memory to run HAXM", AccelerationErrorSolution.SolutionCode.NONE, "Get more available memory for HAXM"),
  CANNOT_INSTALL_ON_THIS_OS(-5, "HAXM can only be installed on Windows or Mac.", AccelerationErrorSolution.SolutionCode.NONE, "Please file a bug against Android Studio."),
  PLATFORM_TOOLS_UPDATE_ADVISED(-6, "Platform tools update is available", AccelerationErrorSolution.SolutionCode.UPDATE_PLATFORM_TOOLS, "Please download platform tools"),
  SYSTEM_IMAGE_UPDATE_ADVISED(-7, "System image update is available", AccelerationErrorSolution.SolutionCode.UPDATE_SYSTEM_IMAGES, "Please update system images");

  companion object {
    @JvmStatic
    fun fromExitCode(code: Int): AccelerationErrorCode = when (code) {
      0 -> ALREADY_INSTALLED
      1 -> NESTED_NOT_SUPPORTED
      2 -> INTEL_REQUIRED
      3 -> NO_CPU_SUPPORT
      4 -> NO_CPU_VTX_SUPPORT
      5 -> NO_CPU_NX_SUPPORT
      6 -> if (SystemInfo.isLinux) ACCELERATION_NOT_INSTALLED_LINUX else if (SystemInfo.isWindows && isAMD) ACCELERATION_NOT_INSTALLED_WIN_AMD else ACCELERATION_NOT_INSTALLED_WIN_MAC_INTEL
      7 -> ACCELERATION_OBSOLETE
      8 -> if (SystemInfo.isLinux) DEV_NOT_FOUND_LINUX else if (SystemInfo.isWindows && isAMD) DEV_NOT_FOUND_WIN_AMD else DEV_NOT_FOUND_WIN_MAC_INTEL
      9 -> VT_DISABLED
      10 -> NX_DISABLED
      11 -> if (SystemInfo.isLinux) DEV_PERMISSION_LINUX else if (SystemInfo.isWindows && isAMD) DEV_PERMISSION_WIN_AMD else DEV_PERMISSION_WIN_MAC_INTEL
      12 -> if (SystemInfo.isLinux) DEV_OPEN_FAILED_LINUX else if (SystemInfo.isWindows && isAMD) DEV_OPEN_FAILED_WIN_AMD else DEV_OPEN_FAILED_WIN_MAC_INTEL
      13 -> if (SystemInfo.isLinux) DEV_IOCTL_FAILED_LINUX else if (SystemInfo.isWindows && isAMD) DEV_IOCTL_FAILED_WIN_AMD else DEV_IOCTL_FAILED_WIN_MAC_INTEL
      14 -> if (SystemInfo.isLinux) DEV_OBSOLETE_LINUX else if (SystemInfo.isWindows && isAMD) DEV_OBSOLETE_WIN_AMD else DEV_OBSOLETE_WIN_MAC_INTEL
      15 -> HYPER_V_ENABLED
      else -> UNKNOWN_ERROR
    }
  }

}