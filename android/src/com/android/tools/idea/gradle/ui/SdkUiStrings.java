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
package com.android.tools.idea.gradle.ui;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;

public final class SdkUiStrings {
  public static final String JDK_LOCATION_WARNING_URL =
    "https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:why_is_there_more_than_one_daemon_process_on_my_machine";
  public static final String JDK_LOCATION_TOOLTIP = "To share the same Gradle daemon between " +
                                                    ApplicationNamesInfo.getInstance().getFullProductName() +
                                                    " and other " +
                                                    "external processes, create a JAVA_HOME environment variable with a valid " +
                                                    "JDK location and select it from the dropdown below.";
  private static final String CHOOSE_VALID_JDK_DIRECTORY_ERR_FORMAT = "Please choose a valid JDK %s directory.";

  @NotNull
  public static String generateChooseValidJdkDirectoryError() {
    return String.format(SdkUiStrings.CHOOSE_VALID_JDK_DIRECTORY_ERR_FORMAT,
                         IdeSdks.getInstance().getRunningVersionOrDefault().getDescription());
  }
}
