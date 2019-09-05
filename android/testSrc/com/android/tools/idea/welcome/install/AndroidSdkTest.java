/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.welcome.install;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.TestUtils;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AndroidSdkTest {
  @Test
  public void getRequiredSdkPackagesDoesntReturnEmulatorOnChromeOs() {
    // Arrange
    AndroidSdk sdk = new AndroidSdk(Mockito.mock(ScopedStateStore.class), true);
    sdk.updateState(AndroidSdkHandler.getInstance(TestUtils.getSdk()));

    // Act
    Object packages = sdk.getRequiredSdkPackages(true);

    // Assert
    assertEquals(Collections.singletonList("platform-tools"), packages);
  }
}
