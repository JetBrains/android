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
package com.android.tools.idea.sdk.install;

import com.android.repository.api.InstallerFactory;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.install.patch.PatchInstallerFactory;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;

public class StudioSdkInstallerUtilTest {
  @Test
  public void testSdkPatchesDisablementAndCorrectInstallerFactory() {
    FakeSettingsController settingsController = new FakeSettingsController(true);
    settingsController.setDisableSdkPatches(false);
    StudioSdkInstallerUtil sdkInstallerUtil = new StudioSdkInstallerUtil(settingsController);

    MockFileOp fop = new MockFileOp();
    final AndroidSdkHandler sdkHandler = new AndroidSdkHandler(null, new File("/sdk"), fop);
    InstallerFactory factory = sdkInstallerUtil.doCreateInstallerFactory(sdkHandler);
    assertThat(factory).isInstanceOf(PatchInstallerFactory.class);

    settingsController.setDisableSdkPatches(true);
    factory = sdkInstallerUtil.doCreateInstallerFactory(sdkHandler);
    assertThat(factory).isInstanceOf(BasicInstallerFactory.class);
  }
}
