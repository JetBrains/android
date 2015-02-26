/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.ScopedStateStore.Key;
import com.android.tools.idea.wizard.ScopedStateStore.Scope;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Guides the user through setting up the JDK location.
 */
public class SetupJdkPath extends DynamicWizardPath {
  private static Key<String> KEY_JDK_LOCATION = ScopedStateStore.createKey("jdk.location", Scope.PATH, String.class);
  @NotNull private final FirstRunWizardMode myMode;
  private final boolean myHasCompatibleJdk;
  private JdkLocationStep myJdkLocationStep;

  public SetupJdkPath(@NotNull FirstRunWizardMode mode) {
    myMode = mode;
    myJdkLocationStep = new JdkLocationStep(KEY_JDK_LOCATION, myMode);
    myHasCompatibleJdk = hasCompatibleJdk();
  }

  private static boolean hasCompatibleJdk() {
    JavaSdk sdkType = JavaSdk.getInstance();
    List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(sdkType);
    for (Sdk jdk : jdks) {
      if (sdkType.isOfVersionOrHigher(jdk, FirstRunWizardDefaults.MIN_JDK_VERSION)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isPathVisible() {
    return !myHasCompatibleJdk;
  }

  @Override
  protected void init() {
    String path = null;
    File javaDir = myMode.getJavaDir();
    if (javaDir != null) {
      path = javaDir.getAbsolutePath();
    }
    else {
      Sdk jdk = DefaultSdks.getDefaultJdk(FirstRunWizardDefaults.MIN_JDK_VERSION);
      if (jdk != null) {
        path = jdk.getHomePath();
      }
    }
    myState.put(KEY_JDK_LOCATION, path);
    addStep(myJdkLocationStep);
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup JDK";
  }

  @Override
  public boolean performFinishingActions() {
    String path = myState.get(KEY_JDK_LOCATION);
    assert path != null;
    DefaultSdks.setDefaultJavaHome(new File(path));
    return true;
  }

  public boolean showsStep() {
    return isPathVisible() && myJdkLocationStep.isStepVisible();
  }
}
