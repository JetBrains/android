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

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static com.android.repository.testframework.FakePackage.FakeRemotePackage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.repository.Revision;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link PatchInstallerFactory}
 */

public class PatchInstallerFactoryTest {
  private PatchInstallerFactory myInstallerFactory;
  private RepoManager myRepoManager;
  private RepositoryPackages myRepositoryPackages;
  private final MockFileOp myFileOp = new MockFileOp();
  private final LocalPackage PATCHER_4 = new FakeLocalPackage("patcher;v4", myFileOp.toPath("/sdk/patcher4"));
  private final LocalPackage PATCHER_2 = new FakeLocalPackage("patcher;v2", myFileOp.toPath("/sdk/patcher2"));

  @Before
  public void setUp() {
    myInstallerFactory = new PatchInstallerFactory((runnerPackage, progress) -> Mockito.mock(PatchRunner.class));
    myInstallerFactory.setFallbackFactory(new BasicInstallerFactory());
    myRepositoryPackages = new RepositoryPackages();
    myRepoManager = new FakeRepoManager(myFileOp.toPath("/sdk"), myRepositoryPackages);
  }

  @Test
  public void cantHandleLinuxUninstallWithPatcher() {
    assumeFalse(FileOpUtils.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager));
  }

  @Test
  public void canHandleWindowsUninstallWithPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertTrue(myInstallerFactory.canHandlePackage(p, myRepoManager));
  }

  @Test
  public void cantHandleWindowsUninstallWithLargeFile() {
    assumeTrue(FileOpUtils.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    myFileOp.recordExistingFile(p.getLocation().toAbsolutePath().toString(), new byte[100 * 1024 * 1024]);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager));
  }

  @Test
  public void cantHandleWindowsUninstallWithoutPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager));
  }

  @Test
  public void cantHandleWindowsUninstallOfLatestPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(PATCHER_4, myRepoManager));
  }

  @Test
  public void cantHandleNoPatchOnLinux() {
    assumeFalse(FileOpUtils.isWindows());
    FakeRemotePackage p = new FakeRemotePackage("foo");
    p.setCompleteUrl("http://example.com");
    p.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(p));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager));
  }

  @Test
  public void canHandleOnLinux() {
    assumeFalse(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setCompleteUrl("http://example.com");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void cantHandleWrongPatch() {
    assumeFalse(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1, 1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void canHandleOnWindows() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void canHandleLargeFileOnWindows() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setCompleteUrl("http://example.com");
    remote.getArchive().getComplete().setSize(100 * 1024 * 1024);
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void canHandleNoPatchOnWindowsWithNewPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithLargeFile() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    remote.getArchive().getComplete().setSize(100 * 1024 * 1024);
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void cantHandleNoSrcOnWindows() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithOldPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_2));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithNoPatcher() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager));
  }

  @Test
  public void createPatchUninstaller() {
    assumeTrue(FileOpUtils.isWindows());
    FakeLocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertTrue(myInstallerFactory.createUninstaller(p, myRepoManager) instanceof PatchUninstaller);
  }

  @Test
  public void createFallbackUninstaller() {
    assumeFalse(FileOpUtils.isWindows());
    FakeLocalPackage p = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    assertFalse(myInstallerFactory.createUninstaller(p, myRepoManager) instanceof PatchUninstaller);
  }

  @Test
  public void createInstallerWithPatch() {
    assumeFalse(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp.toPath("tmp")));
    assertTrue(installer instanceof PatchInstaller);
  }

  @Test
  public void createInstallerWithoutPatch() {
    assumeTrue(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp.toPath("tmp")));
    assertTrue(installer instanceof FullInstaller);
  }

  @Test
  public void createFallbackInstaller() {
    assumeFalse(FileOpUtils.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp.toPath("/sdk/foo"));

    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp.toPath("tmp")));
    assertNotNull(installer);
    assertFalse(installer instanceof PatchOperation);
  }
}
