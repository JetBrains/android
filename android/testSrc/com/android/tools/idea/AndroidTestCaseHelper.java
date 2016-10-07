/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.junit.Assert.fail;

public class AndroidTestCaseHelper {
  /**
   * Environment variable pointing to the JDK to be used for tests
   */
  public static final String JDK_HOME_FOR_TESTS = "JDK_HOME_FOR_TESTS";

  @NotNull
  public static File getJdkPath() {
    String jdkHome = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS);
    if (isNullOrEmpty(jdkHome) || !checkForJdk(jdkHome)) {
      fail("Please specify the path to a valid JDK using system property or environment variable " + JDK_HOME_FOR_TESTS);
    }
    return new File(jdkHome);
  }

  @NotNull
  public static File getAndroidSdkPath() {
    String path = AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable(AndroidTestBase.SDK_PATH_PROPERTY);
    if (isNullOrEmpty(path)) {
      String format = "Please specify the path of an Android SDK in the system property or environment variable '%1$s'";
      fail(String.format(format, AndroidTestBase.SDK_PATH_PROPERTY));
    }
    // If we got here is because the path is not null or empty.
    return new File(path);
  }

  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(String... names) {
    for (String name : names) {
      String s = getSystemPropertyOrEnvironmentVariable(name);
      if (!isNullOrEmpty(s)) {
        return s;
      }
    }

    return null;
  }

  @Nullable
  public static String getSystemPropertyOrEnvironmentVariable(@NotNull String name) {
    String s = System.getProperty(name);
    return s == null ? System.getenv(name) : s;
  }

  public static void removeExistingAndroidSdks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    invokeAndWaitIfNeeded((Runnable)() -> ApplicationManager.getApplication().runWriteAction(() -> {
      for (Sdk sdk : table.getAllJdks()) {
        table.removeJdk(sdk);
      }
      PropertiesComponent component = PropertiesComponent.getInstance(ProjectManager.getInstance().getDefaultProject());
      component.setValue("android.sdk.path", null);
    }));
  }
}
