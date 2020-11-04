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
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.google.common.collect.ImmutableList;
import java.io.File;
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
  private MockFileOp myFileOp = new MockFileOp();
  private final LocalPackage PATCHER_4 = new FakeLocalPackage("patcher;v4", myFileOp);
  private final LocalPackage PATCHER_2 = new FakeLocalPackage("patcher;v2", myFileOp);

  @Before
  public void setUp() {
    myInstallerFactory = new PatchInstallerFactory((runnerPackage, progress, fop) -> Mockito.mock(PatchRunner.class));
    myInstallerFactory.setFallbackFactory(new BasicInstallerFactory());
    myRepositoryPackages = new RepositoryPackages();
    File root = new File("/sdk");
    myRepoManager = new FakeRepoManager(root, myRepositoryPackages);
  }

  @Test
  public void cantHandleLinuxUninstallWithPatcher() {
    assumeFalse(myFileOp.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager, myFileOp));
  }

  @Test
  public void canHandleWindowsUninstallWithPatcher() {
    assumeTrue(myFileOp.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertTrue(myInstallerFactory.canHandlePackage(p, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleWindowsUninstallWithLargeFile() {
    assumeTrue(myFileOp.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp);
    myFileOp.recordExistingFile(p.getLocation().toAbsolutePath().toString(), new byte[100 * 1024 * 1024]);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleWindowsUninstallWithoutPatcher() {
    assumeTrue(myFileOp.isWindows());
    LocalPackage p = new FakeLocalPackage("foo", myFileOp);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleWindowsUninstallOfLatestPatcher() {
    assumeTrue(myFileOp.isWindows());
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    assertFalse(myInstallerFactory.canHandlePackage(PATCHER_4, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleNoPatchOnLinux() {
    assumeFalse(myFileOp.isWindows());
    FakeRemotePackage p = new FakeRemotePackage("foo");
    p.setCompleteUrl("http://example.com");
    p.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(p));
    assertFalse(myInstallerFactory.canHandlePackage(p, myRepoManager, myFileOp));
  }

  @Test
  public void canHandleOnLinux() {
    assumeFalse(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setCompleteUrl("http://example.com");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleWrongPatch() {
    assumeFalse(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1, 1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void canHandleOnWindows() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void canHandleLargeFileOnWindows() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setCompleteUrl("http://example.com");
    remote.getArchive().getComplete().setSize(100 * 1024 * 1024);
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void canHandleNoPatchOnWindowsWithNewPatcher() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertTrue(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithLargeFile() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    remote.getArchive().getComplete().setSize(100 * 1024 * 1024);
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleNoSrcOnWindows() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithOldPatcher() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_2));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void cantHandleNoPatchOnWindowsWithNoPatcher() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    assertFalse(myInstallerFactory.canHandlePackage(remote, myRepoManager, myFileOp));
  }

  @Test
  public void createPatchUninstaller() {
    assumeTrue(myFileOp.isWindows());
    FakeLocalPackage p = new FakeLocalPackage("foo", myFileOp);
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(p, PATCHER_4));
    assertTrue(myInstallerFactory.createUninstaller(p, myRepoManager, myFileOp) instanceof PatchUninstaller);
  }

  @Test
  public void createFallbackUninstaller() {
    assumeFalse(myFileOp.isWindows());
    FakeLocalPackage p = new FakeLocalPackage("foo", myFileOp);
    assertFalse(myInstallerFactory.createUninstaller(p, myRepoManager, myFileOp) instanceof PatchUninstaller);
  }

  @Test
  public void createInstallerWithPatch() {
    assumeFalse(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setPatchInfo("foo", new Revision(1));
    remote.setDependencies(ImmutableList.of(new FakeDependency(PATCHER_4.getPath())));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp), myFileOp);
    assertTrue(installer instanceof PatchInstaller);
  }

  @Test
  public void createInstallerWithoutPatch() {
    assumeTrue(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp), myFileOp);
    assertTrue(installer instanceof FullInstaller);
  }

  @Test
  public void createFallbackInstaller() {
    assumeFalse(myFileOp.isWindows());
    FakeRemotePackage remote = new FakeRemotePackage("foo");
    remote.setRevision(new Revision(2));
    remote.setCompleteUrl("http://example.com");
    FakeLocalPackage local = new FakeLocalPackage("foo", myFileOp);
    local.setRevision(new Revision(1));
    myRepositoryPackages.setLocalPkgInfos(ImmutableList.of(local, PATCHER_4));
    myRepositoryPackages.setRemotePkgInfos(ImmutableList.of(remote));
    Installer installer = myInstallerFactory.createInstaller(remote, myRepoManager, new FakeDownloader(myFileOp), myFileOp);
    assertNotNull(installer);
    assertFalse(installer instanceof PatchOperation);
  }
}
