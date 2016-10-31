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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

public class SimulatedSyncErrors {
  private static Key<Runnable> SIMULATED_ERROR_KEY = Key.create("com.android.tools.idea.gradle.sync.simulated.errors");

  private SimulatedSyncErrors() {
  }

  public static void registerSyncErrorToSimulate(@NotNull String error) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(error);
    };
    ApplicationManager.getApplication().putUserData(SIMULATED_ERROR_KEY, failTask);
  }

  public static void simulateRegisteredSyncError() {
    if (isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      Application application = ApplicationManager.getApplication();
      Runnable task = application.getUserData(SIMULATED_ERROR_KEY);
      if (task != null) {
        application.putUserData(SIMULATED_ERROR_KEY, null);
        task.run();
      }
    }
  }
}
