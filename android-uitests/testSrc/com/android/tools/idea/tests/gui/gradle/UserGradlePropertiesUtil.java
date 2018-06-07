/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.util.GradleProperties.getUserGradlePropertiesFile;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.junit.Assert.assertTrue;

public class UserGradlePropertiesUtil {
  /**
   * Generate a backup copy of user gradle.properties file in order to restore it once the tests are finished. This will prevent having
   * different configurations between tests run before and after this class. Some tests in this class make changes to the proxy that could
   * cause other tests to use an incorrect configuration.
   */
  @Nullable
  public static File backupGlobalGradlePropertiesFile() {
    File backupFile = null;
    File userFile = getUserGradlePropertiesFile();
    if (userFile.exists()) {
      File backup = getUserGradlePropertiesBackupFile();
      assertTrue("Could not create backup of global gradle.properties", userFile.renameTo(backup));
      backupFile = userFile;
    }
    return backupFile;
  }

  /**
   * Restore user gradle.properties file content to what it had before running the tests, or delete if it did not exist.
   */
  public static void restoreGlobalGradlePropertiesFile(@Nullable File backupFile) {
    // Delete user file if it exists
    File userFile = getUserGradlePropertiesFile();
    if (userFile.exists()) {
      assertTrue("Unable to delete global gradle.properties file", userFile.delete());
    }
    // Restore backup
    if (backupFile != null && backupFile.exists()) {
      assertTrue("Unable to restore backup of global gradle.properties", backupFile.renameTo(userFile));
    }
  }

  @NotNull
  private static File getUserGradlePropertiesBackupFile() {
    String home = System.getProperty("user.home");
    return new File(new File(home), toSystemDependentName(".gradle/gradle.~properties"));
  }
}
