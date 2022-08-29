/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.android.flags.junit.RestoreFlagRule;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.debugger.engine.DebugProcessImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AndroidPositionManagerFactoryTest {
  @Rule public final MockitoRule myMockitoRule = MockitoJUnit.rule();
  @Rule public final RestoreFlagRule myRestoreFlagRule = new RestoreFlagRule(StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE);
  @Rule public final AndroidProjectRule myAndroidProjectRule = AndroidProjectRule.inMemory();

  @Mock private DebugProcessImpl mockDebugProcessImpl;

  private AndroidPositionManagerFactory myFactory = new AndroidPositionManagerFactory();

  @Before
  public void setup() {
    when(mockDebugProcessImpl.getProject()).thenReturn(myAndroidProjectRule.getProject());
  }

  @Test
  public void createPositionManager_debugDeviceSdkSourcesEnabled() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(true);

    assertThat(myFactory.createPositionManager(mockDebugProcessImpl)).isInstanceOf(AndroidPositionManager.class);
  }

  @Test
  public void createPositionManager_debugDeviceSdkSourcesNotEnabled() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false);

    assertThat(myFactory.createPositionManager(mockDebugProcessImpl)).isInstanceOf(AndroidPositionManagerOriginal.class);
  }
}
