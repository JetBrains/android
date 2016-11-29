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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

/**
 * Tests for {@link InstallSelectedPackagesStep}.
 *
 * TODO: this does not include tests for backgrounded installers or in-process installs.
 */
@RunWith(MockitoJUnitRunner.class)
public class InstallTaskTest {
  private static final File SDK_ROOT = new File("/sdk");

  @Mock private LocalPackage myExisting1;
  @Mock private RemotePackage myAvailable1;
  @Mock private RemotePackage myAvailable2;

  private MockFileOp myFileOp = new MockFileOp();
  private ProgressIndicator myProgressIndicator = new FakeProgressIndicator();

  @Mock InstallerFactory factory;
  @Mock Installer myInstaller;
  @Mock Installer myInstaller2;
  @Mock Uninstaller myUninstaller;
  Map<RepoPackage, PackageOperation> myOperations;
  InstallTask myInstallTask;

  @Before
  public void setUp() {
    Mockito.when(myExisting1.getPath()).thenReturn("p1");
    Mockito.when(myAvailable1.getPath()).thenReturn("p2");
    Mockito.when(myAvailable2.getPath()).thenReturn("p3");
    RepositoryPackages repoPackages = new RepositoryPackages(ImmutableList.of(myExisting1), ImmutableList.of(myAvailable1, myAvailable2));
    AndroidSdkHandler repoHandler = new AndroidSdkHandler(SDK_ROOT, null, myFileOp, new FakeRepoManager(SDK_ROOT, repoPackages));
    RepoManager repoManager = repoHandler.getSdkManager(myProgressIndicator);
    myInstallTask = new InstallTask(factory, repoHandler, new FakeSettingsController(false), myProgressIndicator);
    myInstallTask.setInstallRequests(ImmutableList.of(new UpdatablePackage(myAvailable1), new UpdatablePackage(myAvailable2)));
    myInstallTask.setUninstallRequests(ImmutableList.of(myExisting1));
    Mockito.when(myInstaller.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller2.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myUninstaller.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller.complete(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller2.complete(myProgressIndicator)).thenReturn(true);
    Mockito.when(myUninstaller.complete(myProgressIndicator)).thenReturn(true);

    myOperations = new HashMap<>();
    myOperations.put(myExisting1, myUninstaller);
    myOperations.put(myAvailable1, myInstaller);
    myOperations.put(myAvailable2, myInstaller2);

    Mockito.when(factory.createInstaller(eq(myAvailable1), eq(repoManager), any(), eq(myFileOp))).thenReturn(myInstaller);
    Mockito.when(factory.createInstaller(eq(myAvailable2), eq(repoManager), any(), eq(myFileOp))).thenReturn(myInstaller2);
    Mockito.when(factory.createUninstaller(myExisting1, repoManager, myFileOp)).thenReturn(myUninstaller);
  }

  @Test
  public void prepare() throws Exception {
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);

    assertTrue(failures.isEmpty());
  }

  @Test
  public void prepareWithFallback() throws Exception {
    Installer fallback = Mockito.mock(Installer.class);
    Mockito.when(fallback.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller2.prepare(myProgressIndicator)).thenReturn(false);
    Mockito.when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);
    Mockito.verify(fallback).prepare(myProgressIndicator);

    assertTrue(failures.isEmpty());
    assertEquals(fallback, myOperations.get(myAvailable2));
  }

  @Test
  public void prepareWithDoubleFallback() throws Exception {
    Installer fallback = Mockito.mock(Installer.class);
    Mockito.when(fallback.prepare(myProgressIndicator)).thenReturn(false);
    Installer fallback2 = Mockito.mock(Installer.class);
    Mockito.when(fallback2.prepare(myProgressIndicator)).thenReturn(true);

    Mockito.when(myInstaller2.prepare(myProgressIndicator)).thenReturn(false);
    Mockito.when(myInstaller2.getFallbackOperation()).thenReturn(fallback);
    Mockito.when(fallback.getFallbackOperation()).thenReturn(fallback2);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);
    Mockito.verify(fallback).prepare(myProgressIndicator);
    Mockito.verify(fallback2).prepare(myProgressIndicator);

    assertTrue(failures.isEmpty());
    assertEquals(fallback2, myOperations.get(myAvailable2));
  }

  @Test
  public void prepareWithErrors() throws Exception {
    Mockito.when(myInstaller2.prepare(myProgressIndicator)).thenReturn(false);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);

    assertTrue(failures.contains(myAvailable2));
    assertEquals(1, failures.size());
  }

  @Test
  public void complete() throws Exception {
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures);

    Mockito.verify(myInstaller).complete(myProgressIndicator);
    Mockito.verify(myInstaller2).complete(myProgressIndicator);
    Mockito.verify(myUninstaller).complete(myProgressIndicator);

    assertTrue(failures.isEmpty());
    assertTrue(myOperations.isEmpty());
  }

  @Test
  public void completeWithFallback() throws Exception {
    Installer fallback = Mockito.mock(Installer.class);
    Mockito.when(myInstaller.getFallbackOperation()).thenReturn(fallback);

    Mockito.when(myInstaller.complete(myProgressIndicator)).thenReturn(false);
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures);

    Mockito.verify(myInstaller).complete(myProgressIndicator);
    Mockito.verify(myInstaller2).complete(myProgressIndicator);
    Mockito.verify(myUninstaller).complete(myProgressIndicator);

    assertTrue(failures.isEmpty());
    assertEquals(fallback, myOperations.get(myAvailable1));
    assertEquals(1, myOperations.size());
  }

  @Test
  public void completeWithErrors() throws Exception {
    Mockito.when(myInstaller.complete(myProgressIndicator)).thenReturn(false);
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures);

    Mockito.verify(myInstaller).complete(myProgressIndicator);
    Mockito.verify(myInstaller2).complete(myProgressIndicator);
    Mockito.verify(myUninstaller).complete(myProgressIndicator);

    assertTrue(failures.contains(myAvailable1));
    assertEquals(1, failures.size());
    assertTrue(myOperations.isEmpty());
  }

  @Test
  public void runBasic() throws Exception {
    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));
    InOrder installer1Calls = Mockito.inOrder(myInstaller);
    installer1Calls.verify(myInstaller).prepare(myProgressIndicator);
    installer1Calls.verify(myInstaller).complete(myProgressIndicator);
    InOrder installer2Calls = Mockito.inOrder(myInstaller2);
    installer2Calls.verify(myInstaller2).prepare(myProgressIndicator);
    installer2Calls.verify(myInstaller2).complete(myProgressIndicator);
    InOrder uninstallerCalls = Mockito.inOrder(myUninstaller);
    uninstallerCalls.verify(myUninstaller).prepare(myProgressIndicator);
    uninstallerCalls.verify(myUninstaller).complete(myProgressIndicator);
  }

  @Test
  public void runCallbacks() throws Exception {
    Runnable prepareComplete = Mockito.mock(Runnable.class);
    myInstallTask.setPrepareCompleteCallback(prepareComplete);
    Function<List<RepoPackage>, Void> complete = (Function<List<RepoPackage>, Void>)Mockito.mock(Function.class);
    myInstallTask.setCompleteCallback(complete);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    InOrder callbackCalls = Mockito.inOrder(myInstaller, prepareComplete, complete);
    callbackCalls.verify(myInstaller).prepare(myProgressIndicator);
    callbackCalls.verify(prepareComplete).run();
    callbackCalls.verify(myInstaller).complete(myProgressIndicator);
    callbackCalls.verify(complete).apply(new ArrayList<>());
  }

  @Test
  public void runWithFallbackOnPrepare() throws Exception {
    Installer fallback = Mockito.mock(Installer.class);
    Mockito.when(fallback.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller2.prepare(myProgressIndicator)).thenReturn(false);
    Mockito.when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);
    Mockito.verify(fallback).prepare(myProgressIndicator);

    Mockito.verify(myInstaller).complete(myProgressIndicator);
    Mockito.verify(myUninstaller).complete(myProgressIndicator);
    Mockito.verify(fallback).complete(myProgressIndicator);
    Mockito.verify(myInstaller2, Mockito.never()).complete(myProgressIndicator);
  }

  @Test
  public void runWithFallbackOnComplete() throws Exception {
    Installer fallback = Mockito.mock(Installer.class);
    Mockito.when(fallback.prepare(myProgressIndicator)).thenReturn(true);
    Mockito.when(myInstaller2.complete(myProgressIndicator)).thenReturn(false);
    Mockito.when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    Mockito.verify(myInstaller).prepare(myProgressIndicator);
    Mockito.verify(myInstaller2).prepare(myProgressIndicator);
    Mockito.verify(myUninstaller).prepare(myProgressIndicator);
    Mockito.verify(fallback).prepare(myProgressIndicator);

    Mockito.verify(myInstaller).complete(myProgressIndicator);
    Mockito.verify(myUninstaller).complete(myProgressIndicator);
    Mockito.verify(fallback).complete(myProgressIndicator);
    Mockito.verify(myInstaller2).complete(myProgressIndicator);
  }
}
