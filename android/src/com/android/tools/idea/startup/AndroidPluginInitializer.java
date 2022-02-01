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
package com.android.tools.idea.startup;

import com.android.tools.idea.progress.StudioProgressManagerAdapter;
import com.intellij.analytics.AndroidStudioAnalytics;
import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.application.Application;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * Initialization code common between Android Studio and Android plugin in IntelliJ.
 */
@SuppressWarnings("UnstableApiUsage")
public class AndroidPluginInitializer implements ApplicationLoadListener {
  @Override
  public void beforeApplicationLoaded(@NotNull Application application, @NotNull Path configPath) {
    AndroidStudioAnalytics.initialize(new AndroidStudioAnalyticsImpl());
    StudioProgressManagerAdapter.initialize();
    ApkFacetCheckerInitializer.initializeApkFacetChecker();
  }
}
