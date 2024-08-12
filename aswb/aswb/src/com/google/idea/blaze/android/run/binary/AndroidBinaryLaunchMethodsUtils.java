/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import com.google.common.annotations.VisibleForTesting;

/**
 * A class that provides function to convert between check box selection and binary launch method.
 */
public class AndroidBinaryLaunchMethodsUtils {

  /** Get binary launch method according to users' selection. */
  static AndroidBinaryLaunchMethod getLaunchMethod(boolean useMobileInstall) {
    return useMobileInstall
        ? AndroidBinaryLaunchMethod.MOBILE_INSTALL
        : AndroidBinaryLaunchMethod.NON_BLAZE;
  }

  /** Get check box selection result according to binary launch method in run configuration file. */
  static boolean useMobileInstall(AndroidBinaryLaunchMethod androidBinaryLaunchMethod) {
    return androidBinaryLaunchMethod != AndroidBinaryLaunchMethod.NON_BLAZE;
  }

  /** All possible binary launch methods. */
  @VisibleForTesting
  public enum AndroidBinaryLaunchMethod {
    NON_BLAZE,
    // Both MOBILE_INSTALL methods have merged.
    // Keep both for backwards compatibility, but in the code both are treated equally.
    // MOBILE_INSTALL is the correct value to use throughout.
    MOBILE_INSTALL,
    MOBILE_INSTALL_V2,
  }
}
