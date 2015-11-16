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

/**
 * Solution strings used in {@link AccelerationErrorCode}.
 */
public class AccelerationErrorSolution {
  static final String SOLUTION_NESTED_VIRTUAL_MACHINE =
    "Unfortunately, the Android Emulator can't support virtual machine acceleration from within a virtual machine.\n" +
    "Here are some of your options:\n" +
    " 1) Use a physical device for testing\n" +
    " 2) Start the emulator on a non-virtualized operating system\n" +
    " 3) Use an Android Virtual Device based on an ARM system image (This is 10x slower than hardware accelerated virtualization)\n";

  static final String SOLUTION_ACCERATION_NOT_SUPPORTED =
    "Unfortunately, your computer does not support hardware accelerated virtualization.\n" +
    "Here are some of your options:\n" +
    " 1) Use a physical device for testing\n" +
    " 2) Develop on a Windows/OSX computer with an Intel processor that supports VT-x and NX\n" +
    " 3) Develop on a Linux computer that supports VT-x or SVM\n" +
    " 4) Use an Android Virtual Device based on an ARM system image\n" +
    "   (This is 10x slower than hardware accelerated virtualization)\n";

  static final String SOLUTION_TURN_OFF_HYPER_V =
    "Unfortunately, you cannot have Hyper-V running and use the emulator.\n" +
    "Here is what you can do:\n" +
    "  1) Start a command prompt as Administrator\n" +
    "  2) Run the following command: C:\\Windows\\system32> bcdedit /set hypervisorlaunchtype off\n" +
    "  3) Reboot your machine.\n";

  /**
   * Solution to problems that we can fix from Android Studio.
   */
  public enum SolutionCode {
    NONE,
    INSTALL_HAXM,
    REINSTALL_HAXM,
  }
}
