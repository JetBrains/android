/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.google.idea.testing.runfiles.Runfiles;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.rules.ExternalResource;

/** Runs before Android Studio integration tests. */
public class AndroidIntegrationTestSetupRule extends ExternalResource {

  private final boolean legacyMode;

  public AndroidIntegrationTestSetupRule(boolean legacyMode) {
    this.legacyMode = legacyMode;
  }

  private final Disposable testRootDisposable = Disposer.newDisposable();
  @Override
  protected void before() throws Throwable {
    System.setProperty("android.studio.sdk.manager.disabled", "true");
    if (legacyMode) {
      System.setProperty("blaze.experiment.querysync.temporary.reenable.legacy.sync", "1");
    }
    Path runfilesPath = Runfiles.runfilesPath();
    final var runFilesRoot = runfilesPath.toRealPath().toString();
    VfsRootAccess.allowRootAccess(testRootDisposable, runFilesRoot);
    // NOTE: This file matters when running test in a bazel sandbox (i.e. not with --config=remote).
    // Please do not remove just because the test passes in pre-submit.
    Path mcpKotlinSdkPath =
      runfilesPath.resolve("prebuilts/tools/common/m2/mcp-sdk-libraries.jar").toRealPath();
    if (!Files.exists(mcpKotlinSdkPath)) {
      // Comment out this and allowRootAccess below and run baze test without --config=remote to
      // find out whether it is still needed and if so which file does not pass the allow list
      // filter.
      throw new IllegalStateException("prebuilts/tools/common/m2/mcp-sdk-libraries.jar not found");
    }
    VfsRootAccess.allowRootAccess(testRootDisposable, mcpKotlinSdkPath.toString());


    // AS 3.3 and higher requires analytics to be initialized, or it'll try to create a file in
    // the home directory (~/.android/...)
    AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
    analyticsSettings.setOptedIn(false);
    AnalyticsSettings.setInstanceForTest(analyticsSettings);
  }

  @Override
  protected void after() {
    Disposer.dispose(testRootDisposable);
  }
}
