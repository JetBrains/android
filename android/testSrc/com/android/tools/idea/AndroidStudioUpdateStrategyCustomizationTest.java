// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import org.junit.Test;

public class AndroidStudioUpdateStrategyCustomizationTest {

  private AndroidStudioUpdateStrategyCustomization updateStrategyCustomization = new AndroidStudioUpdateStrategyCustomization();

  @Test
  public void testParseVersionName() {
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Arctic Fox | 2020.3.1 Patch 3")).isEqualTo(ChannelStatus.RELEASE);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Bumblebee | 2021.1.1 Beta 3")).isEqualTo(ChannelStatus.BETA);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Chipmunk | 2021.2.1 Canary 4")).isEqualTo(ChannelStatus.EAP);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("dev build")).isEqualTo(ChannelStatus.EAP);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Canary 1")).isEqualTo(ChannelStatus.EAP);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar CaNarY")).isEqualTo(ChannelStatus.EAP);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Beta")).isEqualTo(ChannelStatus.BETA);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar RC")).isEqualTo(ChannelStatus.BETA);

    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar")).isEqualTo(ChannelStatus.RELEASE);
    assertThat(updateStrategyCustomization.versionNameToChannelStatus("Foobar Release")).isEqualTo(ChannelStatus.RELEASE);
  }
}
