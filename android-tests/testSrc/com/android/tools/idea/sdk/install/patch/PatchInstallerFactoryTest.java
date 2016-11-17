/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install.patch;

import com.android.repository.Revision;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static com.android.repository.testframework.FakePackage.FakeRemotePackage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link PatchInstallerFactory}
 */

public class PatchInstallerFactoryTest {
  private PatchInstallerFactory myInstallerFactory;
  private AndroidSdkHandler mySdkHandler;
  private RepoManager myRepoManager;
  private RepositoryPackages myRepositoryPackages;
  private MockFileOp myFileOp;
  private static final LocalPackage PATCHER_3 = new FakeLocalPackage("patcher;v3");
  private static final LocalPackage PATCHER_2 = new FakeLocalPackage("patcher;v2");

  private static <T extends RepoPackage> Map<String, T> buildPackageMap(T... packages) {
    return Arrays.stream(packages).collect(Collectors.toMap(RepoPackage::getPath, Function.identity()));
  }

  @Before
  public void setUp() {
    myFileOp = new MockFileOp();
    myInstallerFactory = new PatchInstallerFactory();
    myRepositoryPackages = new RepositoryPackages();
    File root = new File("/sdk");
    myRepoManager = new FakeRepoManager(root, myRepositoryPackages);
    mySdkHandler = new AndroidSdkHandler(root, null, myFileOp, myRepoManager);
  }

  @Test
  public void cantHandleLinuxUninstallWithPatcher() {
    LocalPackage p = new FakeLocalPackage("foo");
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(p, PATCHER_3));
    assertFalse(PatchInstallerFactory.canHandlePackage(p, mySdkHandler));
  }

  @Test
  public void canHandleWindowsUninstallWithPatcher() {
    myFileOp.setIsWindows(true);
    LocalPackage p = new FakeLocalPackage("foo");
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(p, PATCHER_3));
    assertTrue(PatchInstallerFactory.canHandlePackage(p, mySdkHandler));
  }

  @Test
  public void cantHandleWindowsUninstallWithoutPatcher() {
    myFileOp.setIsWindows(true);
    LocalPackage p = new FakeLocalPackage("foo");
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(p));
    assertFalse(PatchInstallerFactory.canHandlePackage(p, mySdkHandler));
  }

  @Test
  public void cantHandleWindowsUninstallOfLatestPatcher() {
    myFileOp.setIsWindows(true);
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(PATCHER_3));
    assertFalse(PatchInstallerFactory.canHandlePackage(PATCHER_3, mySdkHandler));
  }

  @Test
  public void cantHandleNoPatchOnLinux() {
    FakeRemotePackage p = new FakeRemotePackage("foo");
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(p));
    assertFalse(PatchInstallerFactory.canHandlePackage(p, mySdkHandler));
  }

  @Test
  public void canHandleOnLinux() {
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertTrue(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void cantHandleWrongPatch() {
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1, 1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertFalse(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void canHandleOnWindows() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertTrue(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void canHandleNoPatchOnWindowsWithNewPatcher() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertTrue(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void cantHandleNoSrcOnWindows() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertFalse(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithOldPatcher() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_2));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertFalse(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithNoPatcher() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    assertFalse(PatchInstallerFactory.canHandlePackage(remote, mySdkHandler));
  }

  @Test
  public void createUninstaller() {
    FakeLocalPackage p = new FakeLocalPackage("foo");
    assertTrue(myInstallerFactory.createUninstaller(p, myRepoManager, myFileOp) instanceof PatchUninstaller);
  }

  @Test
  public void createInstallerWithPatch() {
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp), myFileOp);
    assertTrue(installer instanceof PatchInstaller);
  }

  @Test
  public void createInstallerWithoutPatch() {
    myFileOp.setIsWindows(true);
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    FakeLocalPackage local = new FakeLocalPackage("foo");
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(buildPackageMap(local, PATCHER_3));
    myRepositoryPackages.setRemotePkgInfos(buildPackageMap(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp), myFileOp);
    assertTrue(installer instanceof FullInstaller);
  }
}
