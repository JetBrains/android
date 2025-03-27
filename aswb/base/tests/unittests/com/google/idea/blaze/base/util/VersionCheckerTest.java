/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.testing.IntellijRule;
import java.time.ZonedDateTime;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Rule;
import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.Nullable;

@RunWith(JUnit4.class)
public class VersionCheckerTest {
  @Rule public IntellijRule intellij = new IntellijRule();

  @Test
  public void testCheckIfNeedToNotify_versionMatched() {
    String expectedVersion = VersionChecker.readBuildNumberFromProductInfo();
    TestApplicationInfo testApplicationInfo = new TestApplicationInfo(BuildNumber.fromString(expectedVersion));
    intellij.registerApplicationService(ApplicationInfo.class, testApplicationInfo);
    assertThat(VersionChecker.versionMismatch()).isFalse();
  }

  @Test
  public void testCheckIfNeedToNotify_versionNotMatched() {
    TestApplicationInfo testApplicationInfo = new TestApplicationInfo(BuildNumber.fromString("AI-1.2.3.4.12345"));
    intellij.registerApplicationService(ApplicationInfo.class, testApplicationInfo);
    assertThat(VersionChecker.versionMismatch()).isTrue();
  }

  private static class TestApplicationInfo extends ApplicationInfo {
    private final BuildNumber buildNumber;

    TestApplicationInfo(BuildNumber buildNumber) {
      this.buildNumber = buildNumber;
    }

    @Override
    public Calendar getBuildDate() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull ZonedDateTime getBuildTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull BuildNumber getBuild() {
      return buildNumber;
    }

    @Override
    public @NotNull String getApiVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getMajorVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getMinorVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getMicroVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getPatchVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getVersionName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getCompanyName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe String getShortCompanyName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getCompanyURL() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getProductUrl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getJetBrainsTvUrl() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasHelp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasContextHelp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe @NotNull String getFullVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NlsSafe @NotNull String getStrictVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getFullApplicationName() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEssentialPlugin(@NotNull String pluginId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEssentialPlugin(@NotNull PluginId pluginId) {
      throw new UnsupportedOperationException();
    }
  }
}
