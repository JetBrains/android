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
package com.android.tools.idea.welcome.wizard;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.config.JdkDetection;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Guides the user through setting up the JDK location.
 */
public class SetupJdkPath extends DynamicWizardPath {
  private static Key<String> KEY_JDK_LOCATION = ScopedStateStore.createKey("jdk.location", Scope.PATH, String.class);
  @NotNull private final FirstRunWizardMode myMode;
  private boolean myHasCompatibleJdk;
  private JdkLocationStep myJdkLocationStep;

  public SetupJdkPath(@NotNull FirstRunWizardMode mode) {
    myMode = mode;
    myJdkLocationStep = new JdkLocationStep(KEY_JDK_LOCATION, myMode);
  }

  @Override
  public boolean isPathVisible() {
    return !myHasCompatibleJdk;
  }

  @Override
  protected void init() {
    if (myHasCompatibleJdk) {
      return;
    }
    String path = null;
    File javaDir = myMode.getJavaDir();
    if (javaDir != null) {
      path = javaDir.getAbsolutePath();
    }
    if (StringUtil.isEmpty(path)) {
      Sdk jdk = IdeSdks.getJdk(FirstRunWizardDefaults.MIN_JDK_VERSION);
      if (jdk != null) {
        path = jdk.getHomePath();
      }
    }
    if (StringUtil.isEmpty(path)) {
      final StringBuilder result = new StringBuilder();
      JdkDetection.start(new JdkDetection.JdkDetectionResult() {
        @Override
        public void onSuccess(String newJdkPath) {
          result.append(newJdkPath);
        }

        @Override
        public void onCancel() {
        }
      });
      path = result.toString();
    }

    if (StringUtil.isEmpty(path) || JdkDetection.validateJdkLocation(new File(path)) != null) {
      addStep(myJdkLocationStep);
    }
    else {
      myState.put(KEY_JDK_LOCATION, path);
      myHasCompatibleJdk = true;
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup JDK";
  }

  @Override
  public boolean performFinishingActions() {
    final String path = myState.get(KEY_JDK_LOCATION);
    assert path != null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        IdeSdks.setJdkPath(new File(path));
      }
    });
    return true;
  }

  boolean shouldDownloadingComponentsStepBeShown() {
    return isPathVisible() && myJdkLocationStep.isStepVisible();
  }
}
