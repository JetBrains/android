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

import com.android.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

  public void DISABLEDtestDownloadSeed() throws WizardException, InstallationCancelledException {
    File destination = new File(tempDir, "android-sdk-seed-install");
    File result = InstallComponentsPath.downloadAndUnzipSdkSeed(new InstallContext(tempDir), destination, 1).execute(destination);
    assertNotNull("Operation was canceled", result);
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
    ILogger log = new StdLogger(StdLogger.Level.VERBOSE);
    SdkManager manager = SdkManager.createManager(destination.getAbsolutePath(), log);
    if (manager == null) {
      throw new IOException("SDK not found");
    }

    Collection<IPkgDesc> sdkPackages =
      new AndroidSdk(new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, null)).getRequiredSdkPackages(null);
    Set<String> toInstall = Sets.newHashSet();
    for (IPkgDesc sdkPackage : sdkPackages) {
      if (sdkPackage != null) {
        toInstall.add(sdkPackage.getInstallId());
      }
    }

    ComponentInstaller operation = new ComponentInstaller(null, true);
    ArrayList<String> packagesToDownload = getAndroidSdkPackages(manager, operation);
    operation.installPackages(manager, packagesToDownload, new LoggerForTest());
    manager.reloadSdk(log);
    LocalPkgInfo[] installedPkgs = manager.getLocalSdk().getPkgsInfos(EnumSet.allOf(PkgType.class));

    assertEquals(8, toInstall.size());
    final Map<String, LocalPkgInfo> installed = Maps.newHashMap();
    for (LocalPkgInfo info : installedPkgs) {
      installed.put(info.getDesc().getInstallId(), info);
    }

    System.out.println("Packages list: \n\t" + Joiner.on("\n\t").join(toInstall));

    //// TODO: Why is this always installed?
    //installed.remove("extra-intel-hardware_accelerated_execution_manager");
    //
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

  private static ArrayList<String> getAndroidSdkPackages(SdkManager manager, ComponentInstaller operation) {
    return operation.getPackagesToInstall(manager, Collections
      .singleton(new AndroidSdk(new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, null))), true);
  }

  public void DISABLEDtestComponentsToInstall() {
    File sdkPath = AndroidTestCaseHelper.getAndroidSdkPath();

    SdkManager manager = SdkManager.createManager(sdkPath.getAbsolutePath(), new StdLogger(StdLogger.Level.VERBOSE));
    assert manager != null;

    ComponentInstaller operation = new ComponentInstaller(null, true);
    ArrayList<String> packagesToDownload = getAndroidSdkPackages(manager, operation);

    System.out.println(Joiner.on("\n").join(packagesToDownload));
    assertTrue(packagesToDownload.isEmpty());
  }

  /**
   * Logger implementation to use during tests
   */
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
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