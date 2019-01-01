/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateStrategy;
import com.intellij.openapi.updateSettings.impl.UpdatesInfo;
import com.intellij.openapi.updateSettings.impl.UserUpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

/** Test of {@link UpdateStrategy} with {@link AndroidStudioUpdateStrategyCustomization}. */
public final class AndroidStudioUpdateStrategyTest extends AndroidTestCase {

  public void testUpdateStrategyUsesAndroidStudioVersion() throws Exception {
    @Language("XML") String updatesXml =
      "<products>" +
      "  <product name='Android Studio'>" +
      "    <code>AI</code>" +
      "    <channel id='AI-2-eap' status='eap'>" +
      "      <build number='AI-183.2153.8.34.5078398' version='3.4 Canary 2'/>" +
      "    </channel>" +
      "    <channel id='AI-2-beta' status='beta'>" +
      "      <build number='AI-182.4892.20.33.5078385' version='3.3 Beta 2'/>" +
      "    </channel>" +
      "  </product>" +
      "</products>";

    UserUpdateSettings settings = mock(UserUpdateSettings.class);
    when(settings.getSelectedChannelStatus()).thenReturn(ChannelStatus.EAP);

    assertThat(new UpdateStrategy(BuildNumber.fromString("AI-182.4505.22.34.5070326"), new UpdatesInfo(JDOMUtil.load(updatesXml)), settings)
      .checkForUpdates().getNewBuild().getNumber().asString())
      .isEqualTo("AI-183.2153.8.34.5078398");  // not AI-182.4892.20.33.5078385. This scenario is taken directly from b/117996392.
  }
}
