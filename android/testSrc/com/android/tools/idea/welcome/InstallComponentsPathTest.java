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

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.updater.SdkUpdaterNoWindow;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.android.utils.ILogger;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

/**
 * Test installing Android SDK logic from the first run wizard.
 * <p/>
 * This test is not meant to be ran on build farm, only on the local system.
 */
public class InstallComponentsPathTest extends AndroidTestBase {
  private File tempDir;

  @Override
  public void setUp() throws Exception {
    System.err.println("Tests in '" + getClass().getName() + "' are disabled");
    if (false) {
      super.setUp();
      final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
      myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      myFixture.setUp();
      myFixture.setTestDataPath(getTestDataPath());
      tempDir = Files.createTempDir();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    FileUtil.delete(tempDir);
    myFixture.tearDown();
    super.tearDown();
  }

  public void DISABLEDtestDownloadSeed() throws WizardException {
    File destination = new File(tempDir, "android-sdk-seed-install");
    boolean result = InstallComponentsPath.downloadAndUnzipSdkSeed(new InstallContext(tempDir), destination, 1);
    assertTrue("Operation was canceled", result);
    assertTrue("Destination should exist", destination.isDirectory());

    File[] children = destination.listFiles();
    assertTrue("Destination is empty", children != null && children.length > 0);
    Set<String> expectedDirs = Sets.newHashSet(SdkConstants.FD_ADDONS, SdkConstants.FD_PLATFORMS, SdkConstants.FD_TOOLS);
    for (File child : children) {
      expectedDirs.remove(child.getName());
    }
    assertEquals("Missing folders: " + Joiner.on(", ").join(expectedDirs), 0, expectedDirs.size());
  }

  public void DISABLEDtestNewInstall() throws IOException, WizardException {
    File destination = new File(tempDir, getName());
    InstallContext context = new InstallContext(tempDir);
    InstallComponentsPath.downloadAndUnzipSdkSeed(context, destination, 1);
    ILogger log = new LoggerForTest();
    SdkManager manager = SdkManager.createManager(destination.getAbsolutePath(), log);
    if (manager == null) {
      throw new IOException("SDK not found");
    }

    PkgDesc.Builder[] packages = AndroidSdk.getPackages();
    Set<String> toInstall = Sets.newHashSet();
    for (PkgDesc.Builder sdkPackage : packages) {
      if (sdkPackage != null) {
        toInstall.add(sdkPackage.create().getInstallId());
      }
    }

    InstallComponentsPath.setupSdkComponents(context, destination, Collections.singleton(new AndroidSdk()), 1);
    manager.reloadSdk(log);
    LocalPkgInfo[] installedPkgs = manager.getLocalSdk().getPkgsInfos(EnumSet.allOf(PkgType.class));

    assertEquals(9, toInstall.size());
    final Map<String, LocalPkgInfo> installed = Maps.newHashMap();
    for (LocalPkgInfo info : installedPkgs) {
      installed.put(info.getDesc().getInstallId(), info);
    }

    System.out.println("Packages list: \n\t" + Joiner.on("\n\t").join(toInstall));

    // TODO: This package is not available for install
    toInstall.remove("sample-21");
    // TODO: Why is this always installed?
    installed.remove("extra-intel-hardware_accelerated_execution_manager");

    Set<String> all = ImmutableSet.copyOf(Iterables.concat(toInstall, installed.keySet()));

    Set<String> notInstalled = ImmutableSet.copyOf(Iterables.filter(all, not(in(installed.keySet()))));
    Set<String> shouldntBeenInstalled = ImmutableSet.copyOf(Iterables.filter(all, not(in(toInstall))));

    assertEquals("Not installed: " + Joiner.on(", ").join(notInstalled), 0, notInstalled.size());
    assertEquals("Were installed: " + Joiner.on(", ").join(Iterables.transform(shouldntBeenInstalled, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return String.format("%s (%s)", input, installed.get(input).getShortDescription());
      }
    })), 0, shouldntBeenInstalled.size());
  }

  private static class LoggerForTest extends SdkLoggerIntegration {
    private String myTitle = null;

    @Override
    protected void setProgress(int progress) {
      // No need.
    }

    @Override
    protected void setDescription(String description) {
      // No spamming
    }

    @Override
    protected void setTitle(String title) {
      if (!StringUtil.isEmptyOrSpaces(title) && !Objects.equal(title, myTitle)) {
        System.out.println(title);
        myTitle = title;
      }
    }

    @Override
    protected void lineAdded(String string) {
      System.out.println(string);
    }
  }
}