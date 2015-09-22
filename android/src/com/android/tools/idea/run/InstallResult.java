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
package com.android.tools.idea.run;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The result of an attempted app installation.
 */
class InstallResult {

  public enum FailureCode {
    NO_ERROR,
    DEVICE_NOT_RESPONDING,
    INCONSISTENT_CERTIFICATES,
    INSTALL_FAILED_VERSION_DOWNGRADE,
    INSTALL_FAILED_DEXOPT,
    NO_CERTIFICATE,
    INSTALL_FAILED_OLDER_SDK,
    UNTYPED_ERROR
  }

  @NotNull public final FailureCode failureCode;
  @Nullable public final String failureMessage;
  @Nullable public final String installOutput;

  public InstallResult(@NotNull FailureCode failureCode, @Nullable String failureMessage, @Nullable String installOutput) {
    this.failureCode = failureCode;
    this.failureMessage = failureMessage;
    this.installOutput = installOutput;
  }

  public static InstallResult forLaunchOutput(@NotNull ErrorMatchingReceiver receiver) {
    return new InstallResult(getFailureCode(receiver), receiver.getFailureMessage(), receiver.getOutput().toString());
  }

  private static FailureCode getFailureCode(ErrorMatchingReceiver receiver) {
    String failureMessage = receiver.getFailureMessage();
    if (receiver.getErrorType() == ErrorMatchingReceiver.NO_ERROR && failureMessage == null) {
      return FailureCode.NO_ERROR;
    }

    if ("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES".equals(failureMessage)) {
      return FailureCode.INCONSISTENT_CERTIFICATES;
    }
    else if ("INSTALL_PARSE_FAILED_NO_CERTIFICATES".equals(failureMessage)) {
      return FailureCode.NO_CERTIFICATE;
    }
    else if ("INSTALL_FAILED_VERSION_DOWNGRADE".equals(failureMessage)) {
      return FailureCode.INSTALL_FAILED_VERSION_DOWNGRADE;
    }
    else if ("INSTALL_FAILED_DEXOPT".equals(failureMessage)) {
      return FailureCode.INSTALL_FAILED_DEXOPT;
    }
    else if ("INSTALL_FAILED_OLDER_SDK".equals(failureMessage)) {
      return FailureCode.INSTALL_FAILED_OLDER_SDK;
    }

    return FailureCode.UNTYPED_ERROR;
  }
}
