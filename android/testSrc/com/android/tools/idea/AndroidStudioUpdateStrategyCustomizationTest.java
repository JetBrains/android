// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.ServiceContainerUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class AndroidStudioUpdateStrategyCustomizationTest {
  @Rule
  public EdtRule edtRule = new EdtRule();

  private Disposable disposable;
  private AndroidStudioUpdateStrategyCustomization updateStrategyCustomization = new AndroidStudioUpdateStrategyCustomization();

  @Before
  public void setup() {
    disposable = Disposer.newDisposable();
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
  }

  @Test
  public void testParseVersionName() {
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Arctic Fox | 2020.3.1 Patch 3")).isEqualTo(ChannelStatus.RELEASE);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Bumblebee | 2021.1.1 Beta 3")).isEqualTo(ChannelStatus.BETA);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Chipmunk | 2021.2.1 Canary 4")).isEqualTo(ChannelStatus.EAP);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("dev build")).isEqualTo(ChannelStatus.MILESTONE);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Flamingo | Nightly 2023-04-25")).isEqualTo(ChannelStatus.MILESTONE);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Canary 1")).isEqualTo(ChannelStatus.EAP);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar CaNarY")).isEqualTo(ChannelStatus.EAP);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Beta")).isEqualTo(ChannelStatus.BETA);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar RC")).isEqualTo(ChannelStatus.BETA);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar")).isEqualTo(ChannelStatus.RELEASE);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Release")).isEqualTo(ChannelStatus.RELEASE);
  }


  private void setupApplication(String fullVersion) {
    ApplicationInfo mock = Mockito.mock(ApplicationInfo.class);
    Mockito.when(mock.getFullVersion()).thenReturn(fullVersion);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ApplicationInfo.class, mock, disposable);
  }

  /**
   * Tests that update channel depends only on ApplicationInfo.fullVersion, and don't on Application.eap
   * <p>
   * Please delete if this is causing any flakiness.
   */
  @Test
  @RunsInEdt // MockApplication with no ideEventQueueDispatcher EP: make sure no other activity can use EDT via Application.invokeLater
  public void testChangeDefaultChannel() {
    setupApplication("Android Studio Bumblebee | 2021.1.1 Patch 3");
    assertThat(updateStrategyCustomization.changeDefaultChannel(ChannelStatus.RELEASE)).isEqualTo(ChannelStatus.RELEASE);

    setupApplication("Android Studio Chipmunk  | 2021.2.1 Beta 4");
    assertThat(updateStrategyCustomization.changeDefaultChannel(ChannelStatus.RELEASE)).isEqualTo(ChannelStatus.BETA);

    setupApplication("Android Studio Chipmunk  | 2021.2.1 RC 1");
    assertThat(updateStrategyCustomization.changeDefaultChannel(ChannelStatus.RELEASE)).isEqualTo(ChannelStatus.BETA);

    setupApplication("Android Studio Dolphin   | 2021.3.1 Canary 9");
    assertThat(updateStrategyCustomization.changeDefaultChannel(ChannelStatus.RELEASE)).isEqualTo(ChannelStatus.EAP);

    setupApplication("Android Studio Flamingo | Nightly 2023-04-25");
    assertThat(updateStrategyCustomization.changeDefaultChannel(ChannelStatus.RELEASE)).isEqualTo(ChannelStatus.MILESTONE);
  }
}
