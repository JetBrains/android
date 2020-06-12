// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.welcome.config;

import com.intellij.openapi.projectRoots.DefaultJdkConfigurator;

public class AndroidDefaultJdkConfiguratorImpl implements DefaultJdkConfigurator {
  @Override
  public String guessJavaHome() {
    // we override the behavior from Java plugin
    // DefaultJdkConfiguratorImpl causes to use the latest available jdk by
    // default, but for Android Studio we need it to be the embedded version.
    return null;
  }
}
