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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.ui.GuiTestingService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Key;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimulatedSyncErrors {
  private static Key<RuntimeException> SIMULATED_ERROR_KEY = Key.create("com.android.tools.idea.gradle.sync.simulated.errors");

  private SimulatedSyncErrors() {
  }

  public static void clear() {
    verifyIsTestMode();
    store(null);
  }

  public static void registerSyncErrorToSimulate(@NotNull String errorMessage) {
    registerSyncErrorToSimulate(new RuntimeException(errorMessage));
  }

  public static void registerSyncErrorToSimulate(@NotNull RuntimeException exception) {
    verifyIsTestMode();
    store(exception);
  }

  public static void registerSyncErrorToSimulate(@NotNull String errorMessage, @NotNull File errorFile) {
    registerSyncErrorToSimulate(new Throwable(errorMessage), errorFile);
  }

  public static void registerSyncErrorToSimulate(@NotNull Throwable cause, @NotNull File errorFile) {
    verifyIsTestMode();
    LocationAwareExternalSystemException exception = new LocationAwareExternalSystemException(cause.getMessage(), errorFile.getPath());
    exception.initCause(cause);
    store(exception);
  }

  private static void store(@Nullable RuntimeException exception) {
    ApplicationManager.getApplication().putUserData(SIMULATED_ERROR_KEY, exception);
  }

  public static void simulateRegisteredSyncError() {
    Application application = ApplicationManager.getApplication();
    RuntimeException error = application.getUserData(SIMULATED_ERROR_KEY);
    if (error != null) {
      verifyIsTestMode();
      application.putUserData(SIMULATED_ERROR_KEY, null);
      throw error;
    }
  }

  private static void verifyIsTestMode() {
    if (!isTestMode()) {
      throw new IllegalStateException("Not in unit or UI test mode");
    }
  }

  private static boolean isTestMode() {
    return GuiTestingService.getInstance().isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode();
  }
}
