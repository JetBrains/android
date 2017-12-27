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
import com.android.repository.testframework.*;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.android.tools.idea.util.FutureUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.InOrder;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InstallSelectedPackagesStep}.
 *
 * TODO: this does not include tests for in-process installs.
 */
public class InstallTaskTest extends AndroidTestCase {
  private static final File SDK_ROOT = new File("/sdk");

  private LocalPackage myExisting1;
  private RemotePackage myAvailable1;
  private RemotePackage myAvailable2;

  private MockFileOp myFileOp = new MockFileOp();
  private ProgressIndicator myProgressIndicator = new FakeProgressIndicator();

  private InstallerFactory factory;
  private Installer myInstaller;
  private Installer myInstaller2;
  private Uninstaller myUninstaller;
  private Map<RepoPackage, PackageOperation> myOperations;
  private InstallTask myInstallTask;
  private AndroidSdkHandler mySdkHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExisting1 = spy(new FakePackage.FakeLocalPackage("p1"));
    myAvailable1 = spy(new FakePackage.FakeRemotePackage("p2"));
    myAvailable2 = spy(new FakePackage.FakeRemotePackage("p3"));
    factory = mock(InstallerFactory.class);
    myInstaller = mock(Installer.class);
    myInstaller2 = mock(Installer.class);
    myUninstaller = mock(Uninstaller.class);

    RepositoryPackages repoPackages = new RepositoryPackages(ImmutableList.of(myExisting1), ImmutableList.of(myAvailable1, myAvailable2));

    FakeRepoManager repoManager = new FakeRepoManager(SDK_ROOT, repoPackages);
    mySdkHandler = new AndroidSdkHandler(SDK_ROOT, new File("/sdk"), myFileOp, repoManager);

    myInstallTask = new InstallTask(factory, mySdkHandler, new FakeSettingsController(false), myProgressIndicator);
    myInstallTask.setInstallRequests(ImmutableList.of(new UpdatablePackage(myAvailable1), new UpdatablePackage(myAvailable2)));
    myInstallTask.setUninstallRequests(ImmutableList.of(myExisting1));
    when(myInstaller.prepare(any())).thenReturn(true);
    when(myInstaller2.prepare(any())).thenReturn(true);
    when(myUninstaller.prepare(any())).thenReturn(true);
    when(myInstaller.complete(any())).thenReturn(true);
    when(myInstaller2.complete(any())).thenReturn(true);
    when(myUninstaller.complete(any())).thenReturn(true);

    myOperations = new HashMap<>();
    myOperations.put(myExisting1, myUninstaller);
    myOperations.put(myAvailable1, myInstaller);
    myOperations.put(myAvailable2, myInstaller2);

    when(factory.createInstaller(eq(myAvailable1), eq(repoManager), any(), eq(myFileOp))).thenReturn(myInstaller);
    when(factory.createInstaller(eq(myAvailable2), eq(repoManager), any(), eq(myFileOp))).thenReturn(myInstaller2);
    when(factory.createUninstaller(myExisting1, repoManager, myFileOp)).thenReturn(myUninstaller);
  }

  public void testPrepare() throws Exception {
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());

    assertTrue(failures.isEmpty());
  }

  public void testPrepareWithFallback() throws Exception {
    Installer fallback = mock(Installer.class);
    when(fallback.prepare(any())).thenReturn(true);
    when(myInstaller2.prepare(any())).thenReturn(false);
    when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());
    verify(fallback).prepare(any());

    assertTrue(failures.isEmpty());
    assertEquals(fallback, myOperations.get(myAvailable2));
  }

  public void testPrepareWithDoubleFallback() throws Exception {
    Installer fallback = mock(Installer.class);
    when(fallback.prepare(any())).thenReturn(false);
    Installer fallback2 = mock(Installer.class);
    when(fallback2.prepare(any())).thenReturn(true);

    when(myInstaller2.prepare(any())).thenReturn(false);
    when(myInstaller2.getFallbackOperation()).thenReturn(fallback);
    when(fallback.getFallbackOperation()).thenReturn(fallback2);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());
    verify(fallback).prepare(any());
    verify(fallback2).prepare(any());

    assertTrue(failures.isEmpty());
    assertEquals(fallback2, myOperations.get(myAvailable2));
  }

  public void testPrepareWithErrors() throws Exception {
    when(myInstaller2.prepare(any())).thenReturn(false);

    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.preparePackages(myOperations, failures);

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());

    assertTrue(failures.contains(myAvailable2));
    assertEquals(1, failures.size());
  }

  public void testComplete() throws Exception {
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures, new FakeProgressIndicator(true));

    verify(myInstaller).complete(any());
    verify(myInstaller2).complete(any());
    verify(myUninstaller).complete(any());

    assertTrue(failures.isEmpty());
    assertTrue(myOperations.isEmpty());
  }

  public void testCompleteWithFallback() throws Exception {
    Installer fallback = mock(Installer.class);
    when(myInstaller.getFallbackOperation()).thenReturn(fallback);

    when(myInstaller.complete(any())).thenReturn(false);
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures, new FakeProgressIndicator(true));

    verify(myInstaller).complete(any());
    verify(myInstaller2).complete(any());
    verify(myUninstaller).complete(any());

    assertTrue(failures.isEmpty());
    assertEquals(fallback, myOperations.get(myAvailable1));
    assertEquals(1, myOperations.size());
  }

  public void testCompleteWithErrors() throws Exception {
    when(myInstaller.complete(any())).thenReturn(false);
    List<RepoPackage> failures = new ArrayList<>();
    myInstallTask.completePackages(myOperations, failures, new FakeProgressIndicator(true));

    verify(myInstaller).complete(any());
    verify(myInstaller2).complete(any());
    verify(myUninstaller).complete(any());

    assertTrue(failures.contains(myAvailable1));
    assertEquals(1, failures.size());
    assertTrue(myOperations.isEmpty());
  }

  public void testRunBasic() throws Exception {
    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));
    InOrder installer1Calls = inOrder(myInstaller);
    installer1Calls.verify(myInstaller).prepare(any());
    installer1Calls.verify(myInstaller).complete(any());
    InOrder installer2Calls = inOrder(myInstaller2);
    installer2Calls.verify(myInstaller2).prepare(any());
    installer2Calls.verify(myInstaller2).complete(any());
    InOrder uninstallerCalls = inOrder(myUninstaller);
    uninstallerCalls.verify(myUninstaller).prepare(any());
    uninstallerCalls.verify(myUninstaller).complete(any());
  }

  public void testRunCallbacks() throws Exception {
    Runnable prepareComplete = mock(Runnable.class);
    myInstallTask.setPrepareCompleteCallback(prepareComplete);
    Function<List<RepoPackage>, Void> complete = (Function<List<RepoPackage>, Void>)mock(Function.class);
    myInstallTask.setCompleteCallback(complete);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    InOrder callbackCalls = inOrder(myInstaller, prepareComplete, complete);
    callbackCalls.verify(myInstaller).prepare(any());
    callbackCalls.verify(prepareComplete).run();
    callbackCalls.verify(myInstaller).complete(any());
    callbackCalls.verify(complete).apply(new ArrayList<>());
  }

  public void testRunWithFallbackOnPrepare() throws Exception {
    Installer fallback = mock(Installer.class);
    when(fallback.prepare(any())).thenReturn(true);
    when(myInstaller2.prepare(any())).thenReturn(false);
    when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());
    verify(fallback).prepare(any());

    verify(myInstaller).complete(any());
    verify(myUninstaller).complete(any());
    verify(fallback).complete(any());
    verify(myInstaller2, never()).complete(any());
  }

  public void testRunWithFallbackOnComplete() throws Exception {
    Installer fallback = mock(Installer.class);
    when(fallback.prepare(any())).thenReturn(true);
    when(myInstaller2.complete(any())).thenReturn(false);
    when(myInstaller2.getFallbackOperation()).thenReturn(fallback);

    myInstallTask.run(new StudioProgressIndicatorAdapter(myProgressIndicator, new EmptyProgressIndicator()));

    verify(myInstaller).prepare(any());
    verify(myInstaller2).prepare(any());
    verify(myUninstaller).prepare(any());
    verify(fallback).prepare(any());

    verify(myInstaller).complete(any());
    verify(myUninstaller).complete(any());
    verify(fallback).complete(any());
    verify(myInstaller2).complete(any());
  }

  public void testBackground() throws Exception {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    InstallerFactory factory = mock(InstallerFactory.class);
    when(factory.createInstaller(eq(myAvailable1), any(), any(), any())).thenReturn(myInstaller);

    InstallSelectedPackagesStep installStep =
      new InstallSelectedPackagesStep(new ArrayList<>(ImmutableList.of(new UpdatablePackage(myAvailable1))),
                                      new ArrayList<>(), mySdkHandler, true, factory, false);
    CompletableFuture<Boolean> listenerAdded = new CompletableFuture<>();
    when(myInstaller.prepare(any())).then(invocation -> {
      // wait until the wizard completion listener is added, or maybe we'll be done too early and not see that the wizard is finished.
      listenerAdded.get();
      // This is the background action
      installStep.getExtraAction().actionPerformed(null);
      return true;
    });
    assertNotNull(installStep.getExtraAction());
    ModelWizard wizard = wizardBuilder.addStep(installStep).build();
    CompletableFuture<Boolean> completed = new CompletableFuture<>();
    wizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult result) {
        completed.complete(true);
      }
    });
    listenerAdded.complete(true);
    FutureUtils.pumpEventsAndWaitForFuture(completed, 5, TimeUnit.SECONDS);

    // Wizard will complete after prepare and without running complete.
    verify(myInstaller).prepare(any());
    verify(myInstaller, never()).complete(any());
    assertTrue(wizard.isFinished());
    // This would normally be done by the wizard frame
    Disposer.dispose(wizard);

    when(factory.createInstaller(eq(myAvailable1), any(), any(), any())).thenReturn(myInstaller2);
    InstallSelectedPackagesStep installStep2 =
      new InstallSelectedPackagesStep(new ArrayList<>(ImmutableList.of(new UpdatablePackage(myAvailable1))),
                                      new ArrayList<>(), mySdkHandler, true, factory, false);
    wizardBuilder = new ModelWizard.Builder(installStep2);
    CompletableFuture<Boolean> completed2 = new CompletableFuture<>();
    installStep2.canGoForward().addListener(value -> completed2.complete(true));
    wizard = wizardBuilder.build();
    FutureUtils.pumpEventsAndWaitForFuture(completed2, 5, TimeUnit.SECONDS);
    wizard.goForward();

    assertTrue(wizard.isFinished());
    // now both prepare and complete will run.
    verify(myInstaller2).prepare(any());
    verify(myInstaller2).complete(any());
    // This would normally be done by the wizard frame
    Disposer.dispose(wizard);
  }
}
