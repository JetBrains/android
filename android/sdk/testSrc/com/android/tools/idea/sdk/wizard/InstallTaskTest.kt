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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.DelegatingProgressIndicator
import com.android.repository.api.Installer
import com.android.repository.api.InstallerFactory
import com.android.repository.api.LocalPackage
import com.android.repository.api.PackageOperation
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoPackage
import com.android.repository.api.Uninstaller
import com.android.repository.api.UpdatablePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.repository.testframework.FakeSettingsController
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.waitForCondition
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.observable.InvalidationListener
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizard.WizardListener
import com.android.tools.idea.wizard.model.ModelWizard.WizardResult
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.jetbrains.android.AndroidTestCase
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

/**
 * Tests for [InstallSelectedPackagesStep].
 *
 * TODO: this does not include tests for in-process installs.
 */
class InstallTaskTest : AndroidTestCase() {
  private val sdkRoot = createInMemoryFileSystemAndFolder("sdk")
  private val progressIndicator: ProgressIndicator = FakeProgressIndicator()

  private lateinit var available1: RemotePackage
  private lateinit var available2: RemotePackage
  private lateinit var installer: Installer
  private lateinit var installer2: Installer
  private lateinit var uninstaller: Uninstaller
  private lateinit var operations: MutableMap<RepoPackage, PackageOperation>
  private lateinit var installTask: InstallTask
  private lateinit var sdkHandler: AndroidSdkHandler
  private lateinit var factory: InstallerFactory
  private lateinit var existing1: LocalPackage

  override fun setUp() {
    super.setUp()
    existing1 = spy(FakeLocalPackage("p1", sdkRoot.resolve("p1")))
    available1 = spy(FakeRemotePackage("p2"))
    available2 = spy(FakeRemotePackage("p3"))
    factory = mock()
    installer = mock()
    installer2 = mock()
    uninstaller = mock()

    val repoPackages = RepositoryPackages(listOf(existing1), listOf(available1, available2))
    val repoManager = FakeRepoManager(sdkRoot, repoPackages)
    sdkHandler = AndroidSdkHandler(sdkRoot, sdkRoot, repoManager)

    whenever(installer.prepare(any())).thenReturn(true)
    whenever(installer2.prepare(any())).thenReturn(true)
    whenever(uninstaller.prepare(any())).thenReturn(true)
    whenever(installer.complete(any())).thenReturn(true)
    whenever(installer2.complete(any())).thenReturn(true)
    whenever(uninstaller.complete(any())).thenReturn(true)

    operations =
      mutableMapOf(
        existing1 to uninstaller,
        available1 to installer,
        available2 to installer2,
      )

    whenever(factory.createInstaller(eq(available1), eq(repoManager), any())).thenReturn(installer)
    whenever(factory.createInstaller(eq(available2), eq(repoManager), any())).thenReturn(installer2)
    whenever(factory.createUninstaller(existing1, repoManager)).thenReturn(uninstaller)

    installTask = createInstallTask()
  }

  private fun createInstallTask(
    prepareCompleteCallback: (() -> Unit)? = null,
    completeCallback: ((List<RepoPackage>) -> Unit)? = null,
  ): InstallTask =
    InstallTask(
      installerFactory = factory,
      sdkHandler = sdkHandler,
      settingsController = FakeSettingsController(false),
      logger = progressIndicator,
      installRequests = listOf(UpdatablePackage(available1), UpdatablePackage(available2)),
      uninstallRequests = listOf(existing1),
      prepareCompleteCallback = prepareCompleteCallback,
      completeCallback = completeCallback,
    )

  fun testPrepare() {
    val failures = mutableListOf<RepoPackage>()
    installTask.preparePackages(operations, failures, FakeProgressIndicator())

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())

    assertTrue(failures.isEmpty())
  }

  fun testPrepareWithFallback() {
    val fallback = mock<Installer>()
    whenever(fallback.prepare(any())).thenReturn(true)
    whenever(installer2.prepare(any())).thenReturn(false)
    whenever(installer2.fallbackOperation).thenReturn(fallback)

    val failures = mutableListOf<RepoPackage>()
    installTask.preparePackages(operations, failures, FakeProgressIndicator())

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())
    verify(fallback).prepare(any())

    assertTrue(failures.isEmpty())
    assertEquals(fallback, operations[available2])
  }

  fun testPrepareWithDoubleFallback() {
    val fallback = mock<Installer>()
    whenever(fallback.prepare(any())).thenReturn(false)
    val fallback2 = mock<Installer>()
    whenever(fallback2.prepare(any())).thenReturn(true)

    whenever(installer2.prepare(any())).thenReturn(false)
    whenever(installer2.fallbackOperation).thenReturn(fallback)
    whenever(fallback.fallbackOperation).thenReturn(fallback2)

    val failures = mutableListOf<RepoPackage>()
    installTask.preparePackages(operations, failures, FakeProgressIndicator())

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())
    verify(fallback).prepare(any())
    verify(fallback2).prepare(any())

    assertTrue(failures.isEmpty())
    assertEquals(fallback2, operations[available2])
  }

  fun testPrepareWithErrors() {
    whenever(installer2.prepare(any())).thenReturn(false)

    val failures = mutableListOf<RepoPackage>()
    installTask.preparePackages(operations, failures, FakeProgressIndicator())

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())

    assertTrue(available2 in failures)
    assertEquals(1, failures.size)
  }

  fun testComplete() {
    val failures = mutableListOf<RepoPackage>()
    installTask.completePackages(
      operations,
      failures,
      FakeProgressIndicator(true),
      FakeProgressIndicator(),
    )

    verify(installer).complete(any())
    verify(installer2).complete(any())
    verify(uninstaller).complete(any())

    assertTrue(failures.isEmpty())
    assertTrue(operations.isEmpty())
  }

  fun testCompleteWithFallback() {
    val fallback = mock<Installer>()
    whenever(installer.fallbackOperation).thenReturn(fallback)
    whenever(installer.complete(any())).thenReturn(false)

    val failures = mutableListOf<RepoPackage>()
    installTask.completePackages(
      operations,
      failures,
      FakeProgressIndicator(true),
      FakeProgressIndicator(),
    )

    verify(installer).complete(any())
    verify(installer2).complete(any())
    verify(uninstaller).complete(any())

    assertTrue(failures.isEmpty())
    assertEquals(fallback, operations[available1])
    assertEquals(1, operations.size)
  }

  fun testCompleteWithErrors() {
    whenever(installer.complete(any())).thenReturn(false)

    val failures = mutableListOf<RepoPackage>()
    installTask.completePackages(
      operations,
      failures,
      FakeProgressIndicator(true),
      FakeProgressIndicator(),
    )

    verify(installer).complete(any())
    verify(installer2).complete(any())
    verify(uninstaller).complete(any())

    assertTrue(available1 in failures)
    assertEquals(1, failures.size)
    assertTrue(operations.isEmpty())
  }

  fun testRunBasic() {
    installTask.run(progressIndicator)

    val installer1Calls = inOrder(installer)
    installer1Calls.verify(installer).prepare(any())
    installer1Calls.verify(installer).complete(any())
    val installer2Calls = inOrder(installer2)
    installer2Calls.verify(installer2).prepare(any())
    installer2Calls.verify(installer2).complete(any())
    val uninstallerCalls = inOrder(uninstaller)
    uninstallerCalls.verify(uninstaller).prepare(any())
    uninstallerCalls.verify(uninstaller).complete(any())
  }

  fun testRunCallbacks() {
    val prepareComplete = mock<() -> Unit>()
    val complete = mock<(List<RepoPackage>) -> Unit>()
    val installTask = createInstallTask(prepareCompleteCallback = prepareComplete, completeCallback = complete)

    installTask.run(progressIndicator)

    val callbackCalls = inOrder(installer, prepareComplete, complete)
    callbackCalls.verify(installer).prepare(any())
    callbackCalls.verify(prepareComplete).invoke()
    callbackCalls.verify(installer).complete(any())
    callbackCalls.verify(complete).invoke(emptyList())
  }

  fun testRunWithFallbackOnPrepare() {
    val fallback = mock<Installer>()
    whenever(fallback.prepare(any())).thenReturn(true)
    whenever(installer2.prepare(any())).thenReturn(false)
    whenever(installer2.fallbackOperation).thenReturn(fallback)

    installTask.run(progressIndicator)

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())
    verify(fallback).prepare(any())

    verify(installer).complete(any())
    verify(uninstaller).complete(any())
    verify(fallback).complete(any())
    verify(installer2, never()).complete(any())
  }

  fun testRunWithFallbackOnComplete() {
    val fallback = mock<Installer>()
    whenever(fallback.prepare(any())).thenReturn(true)
    whenever(installer2.complete(any())).thenReturn(false)
    whenever(installer2.fallbackOperation).thenReturn(fallback)

    installTask.run(progressIndicator)

    verify(installer).prepare(any())
    verify(installer2).prepare(any())
    verify(uninstaller).prepare(any())
    verify(fallback).prepare(any())

    verify(installer).complete(any())
    verify(uninstaller).complete(any())
    verify(fallback).complete(any())
    verify(installer2).complete(any())
  }

  fun testBackground() {
    val factory = mock<InstallerFactory>()
    whenever(factory.createInstaller(eq(available1), any(), any())).thenReturn(installer)

    val installStep =
      InstallSelectedPackagesStep(
        installRequests = listOf(UpdatablePackage(available1)),
        sdkHandler = sdkHandler,
        backgroundable = true,
        factory = factory,
      )
    val listenerAdded = CompletableFuture<Boolean>()
    whenever(installer.prepare(any())).thenAnswer {
      // wait until the wizard completion listener is added, or maybe we'll be done too early and not see that the wizard is finished.
      listenerAdded.get()
      // This is the background action
      installStep.extraAction?.actionPerformed(null)
      true
    }
    assertNotNull(installStep.extraAction)
    var wizard = ModelWizard.Builder().addStep(installStep).build()
    val completed = CompletableFuture<Boolean>()
    wizard.addResultListener(
      object : WizardListener {
        override fun onWizardFinished(result: WizardResult) {
          completed.complete(true)
        }
      }
    )
    listenerAdded.complete(true)
    pumpEventsAndWaitForFuture(completed, 5, TimeUnit.SECONDS)

    // Wizard will complete after prepare and without running complete.
    verify(installer).prepare(any())
    verify(installer, never()).complete(any())
    assertTrue(wizard.isFinished)
    // This would normally be done by the wizard frame
    Disposer.dispose(wizard)

    whenever(factory.createInstaller(eq(available1), any(), any())).thenReturn(installer2)
    val installStep2 =
      InstallSelectedPackagesStep(
        installRequests = listOf(UpdatablePackage(available1)),
        sdkHandler = sdkHandler,
        backgroundable = true,
        factory = factory,
      )
    val completed2 = CompletableFuture<Boolean>()
    installStep2.canGoForward().addListener(InvalidationListener { completed2.complete(true) })
    wizard = ModelWizard.Builder(installStep2).build()
    pumpEventsAndWaitForFuture(completed2, 5, TimeUnit.SECONDS)
    wizard.goForward()

    assertTrue(wizard.isFinished)
    // now both prepare and complete will run.
    verify(installer2).prepare(any())
    verify(installer2).complete(any())
    // This would normally be done by the wizard frame
    Disposer.dispose(wizard)
  }

  fun testProgressWithBackgrounding() {
    val factory = mock<InstallerFactory>()
    val p3 = spy(FakeRemotePackage("p4"))
    val p4 = spy(FakeRemotePackage("p5"))
    whenever(factory.createInstaller(eq(available1), any(), any())).thenReturn(installer)
    whenever(factory.createInstaller(eq(available2), any(), any())).thenReturn(installer2)
    val installer3 = mock<Installer>()
    whenever(factory.createInstaller(eq(p3), any(), any())).thenReturn(installer3)
    val installer4 = mock<Installer>()
    whenever(factory.createInstaller(eq(p4), any(), any())).thenReturn(installer4)

    val installStep =
      InstallSelectedPackagesStep(
        installRequests =
          listOf(
            UpdatablePackage(available1),
            UpdatablePackage(available2),
            UpdatablePackage(p3),
            UpdatablePackage(p4),
          ),
        sdkHandler = sdkHandler,
        backgroundable = true,
        factory = factory,
      )
    val listenerAdded = CompletableFuture<Boolean>()
    var capturedProgress: ProgressIndicator? = null
    whenever(installer.prepare(any())).thenAnswer { invocation ->
      val delegating = invocation.arguments[0] as DelegatingProgressIndicator
      capturedProgress = delegating.delegates.first()
      assertEquals(0.0, capturedProgress?.fraction)
      // wait until the wizard completion listener is added, or maybe we'll be done too early and not see that the wizard is finished.
      listenerAdded.get()
      true
    }
    whenever(installer2.prepare(any())).thenAnswer {
      // At this point we're 1/8 done = one preparation out of four preparations + four completions.
      assertEquals(0.125, capturedProgress?.fraction)
      true
    }
    whenever(installer3.prepare(any())).thenAnswer {
      assertEquals(0.25, capturedProgress?.fraction)
      // This is the background action
      installStep.extraAction?.actionPerformed(null)
      true
    }
    whenever(installer4.prepare(any())).thenAnswer {
      // When we background the max progress for preparing will go from 0.5 to 1.0, and we're 3/4 done so far.
      assertEquals(0.75, capturedProgress?.fraction)
      true
    }
    var wizard: ModelWizard? = null
    try {
      wizard = ModelWizard.Builder().addStep(installStep).build()
      val completed = CompletableFuture<Boolean>()
      wizard.addResultListener(
        object : WizardListener {
          override fun onWizardFinished(result: WizardResult) {
            completed.complete(true)
          }
        }
      )
      listenerAdded.complete(true)
      pumpEventsAndWaitForFuture(completed, 5, TimeUnit.SECONDS)
      // The above only waits for the wizard to be completed, but the backgrounded step may not be done yet.
      waitForCondition(5, TimeUnit.SECONDS) { capturedProgress?.fraction == 1.0 }
    } finally {
      wizard?.let { Disposer.dispose(it) }
    }
  }

  private inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)

  private inline fun <reified T : Any> spy(value: T): T = Mockito.spy(value)
}
