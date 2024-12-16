/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.config.InstallerData
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.net.HttpConfigurable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@RunsInEdt
class AndroidStudioWelcomeScreenServiceTest {

  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain =
    RuleChain(projectRule, EdtRule()) // AndroidProjectRule must get initialized off the EDT thread

  @Before
  fun setUp() {
    AndroidStudioWelcomeScreenService.instance.wizardWasShown = false
  }

  @Test
  fun isAvailable_returnsTrue_whenNoWizardShown() {
    assertTrue { AndroidStudioWelcomeScreenProvider().isAvailable }
  }

  @Test
  fun isAvailable_returnsFalse_afterWizardShown() {
    AndroidStudioWelcomeScreenService.instance.wizardWasShown = true

    assertFalse { AndroidStudioWelcomeScreenService.instance.isAvailable() }
  }

  @Test
  fun isAvailable_returnsFalse_whenInGuiTestingMode() {
    GuiTestingService.getInstance().isGuiTestingMode = true

    assertFalse { AndroidStudioWelcomeScreenService.instance.isAvailable() }
  }

  @Test
  fun isAvailable_returnsFalse_whenDisabledInProperties() {
    System.setProperty(AndroidStudioWelcomeScreenService.SYSTEM_PROPERTY_DISABLE_WIZARD, "true")

    assertFalse { AndroidStudioWelcomeScreenService.instance.isAvailable() }
  }

  @Test
  fun getWizardMode_returnsNewInstall_whenSdkNotUpToDate() {
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(false)
    val mockIdeSdks = mock(IdeSdks::class.java)

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        null,
        mockIdeSdks,
      ) == FirstRunWizardMode.NEW_INSTALL
    }
  }

  @Test
  fun getWizardMode_returnsNewInstall_whenAlwaysShowFlagIsSet() {
    StudioFlags.NPW_FIRST_RUN_SHOW.overrideForTest(true, projectRule.fixture.testRootDisposable)
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(true)
    val mockIdeSdks = mock(IdeSdks::class.java)

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        null,
        mockIdeSdks,
      ) == FirstRunWizardMode.NEW_INSTALL
    }
  }

  @Test
  fun getWizardMode_returnsMissingSdk_whenPersistentDataShowsSdkUpToDateButNoSdkInstalled() {
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(true)
    val mockIdeSdks = mock(IdeSdks::class.java)

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        null,
        mockIdeSdks,
      ) == FirstRunWizardMode.MISSING_SDK
    }
  }

  @Test
  fun getWizardMode_returnsNull_whenSdkInstalledAndUpToDate() {
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(true)
    val mockIdeSdks = mock(IdeSdks::class.java)
    whenever(mockIdeSdks.eligibleAndroidSdks).thenReturn(listOf(mock(Sdk::class.java)))

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        null,
        mockIdeSdks,
      ) == null
    }
  }

  @Test
  fun getWizardMode_returnsInstallHandoff_whenInstallerDataIsCurrentVersionAndSdkNotUpToDate() {
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(false)
    val mockInstallerData = mock(InstallerData::class.java)
    whenever(mockInstallerData.isCurrentVersion).thenReturn(true)
    val mockIdeSdks = mock(IdeSdks::class.java)

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        mockInstallerData,
        mockIdeSdks,
      ) == FirstRunWizardMode.INSTALL_HANDOFF
    }
  }

  @Test
  fun getWizardMode_returnsInstallHandoff_whenInstallerDataIsCurrentVersionAndTimestampsDiffer() {
    val mockPersistentData = mock(AndroidFirstRunPersistentData::class.java)
    whenever(mockPersistentData.isSdkUpToDate).thenReturn(true)
    whenever(mockPersistentData.isSameTimestamp(any())).thenReturn(false)

    val mockInstallerData = mock(InstallerData::class.java)
    whenever(mockInstallerData.isCurrentVersion).thenReturn(true)
    val mockIdeSdks = mock(IdeSdks::class.java)

    assertTrue {
      AndroidStudioWelcomeScreenService.instance.getWizardMode(
        mockPersistentData,
        mockInstallerData,
        mockIdeSdks,
      ) == FirstRunWizardMode.INSTALL_HANDOFF
    }
  }

  @Test
  fun checkInternetConnection_showsIdeaMessageDialog_whenThereAreNetworkIssues() {
    val mockHttpConfigurable = mock(HttpConfigurable::class.java)
    whenever(mockHttpConfigurable.openHttpConnection(any())).thenThrow(IOException::class.java)

    mockStatic(Messages::class.java).use { mockMessages ->
      whenever(
          Messages.showIdeaMessageDialog(
            anyOrNull(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
          )
        )
        .thenReturn(1)
      val checkComplete = CompletableFuture<Boolean>()
      executeOnPooledThread {
        AndroidStudioWelcomeScreenService.instance.checkInternetConnection(mockHttpConfigurable)
        checkComplete.complete(true)
      }
      pumpEventsAndWaitForFuture(checkComplete, 5, TimeUnit.SECONDS)

      mockMessages.verify {
        Messages.showIdeaMessageDialog(
          anyOrNull(),
          any(),
          any(),
          any(),
          any(),
          anyOrNull(),
          anyOrNull(),
        )
      }
    }
  }

  @Test
  fun checkInternetConnection_doesNotCrash_whenUnknownErrorThrown() {
    val mockHttpConfigurable = mock(HttpConfigurable::class.java)
    whenever(mockHttpConfigurable.openHttpConnection(any()))
      .thenThrow(NoClassDefFoundError::class.java)

    val checkComplete = CompletableFuture<Boolean>()
    executeOnPooledThread {
      AndroidStudioWelcomeScreenService.instance.checkInternetConnection(mockHttpConfigurable)
      checkComplete.complete(true)
    }
    pumpEventsAndWaitForFuture(checkComplete, 5, TimeUnit.SECONDS)
  }
}
