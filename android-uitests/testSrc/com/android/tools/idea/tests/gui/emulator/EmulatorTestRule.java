/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

public class EmulatorTestRule extends ExternalResource {

  public static final long DEFAULT_EMULATOR_WAIT_SECONDS = 240;

  private static final String DEFAULT_AVD_NAME = "device under test";

  @NotNull
  public String getDefaultAvdName() { return DEFAULT_AVD_NAME; }

  @Override
  protected void before() throws Throwable {
    MockAvdManagerConnection.inject();
    getEmulatorConnection().killEmulatorProcesses();
    // Remove all AVDs
    for (AvdInfo avdInfo: getEmulatorConnection().getAvds(true)) {
      getEmulatorConnection().deleteAvd(avdInfo);
    }
  }

  @Override
  protected void after() {
    getEmulatorConnection().killEmulator();
    // Remove all AVDs
    for (AvdInfo avdInfo: getEmulatorConnection().getAvds(true)) {
      getEmulatorConnection().deleteAvd(avdInfo);
    }
  }

  public void createAVD(AvdManagerDialogFixture avdManagerDialog,
                        String tab,
                        ChooseSystemImageStepFixture.SystemImage image,
                        String avdName) {
    createAVD(avdManagerDialog, "Nexus 5", tab, image, avdName);
  }

  public void createAVD(AvdManagerDialogFixture avdManagerDialog,
                        String hardwareProfile,
                        String tab,
                        ChooseSystemImageStepFixture.SystemImage image,
                        String avdName) {
    avdManagerDialog.createNew()
      .selectHardware()
      .selectHardwareProfile(hardwareProfile)
      .wizard()
      .clickNext()
      .getChooseSystemImageStep()
      .selectTab(tab)
      .selectSystemImage(image)
      .wizard()
      .clickNext()
      .getConfigureAvdOptionsStep()
      .setAvdName(avdName)
      .selectGraphicsSoftware()
      .wizard()
      .clickFinish();

    avdManagerDialog.close();
  }

  public void createDefaultAVD(AvdManagerDialogFixture avdManagerDialog) {
    createAVD(avdManagerDialog,
              "x86 Images",
              new ChooseSystemImageStepFixture.SystemImage("Nougat", "24", "x86", "Android 7.0"),
              DEFAULT_AVD_NAME);
  }

  public MockAvdManagerConnection getEmulatorConnection() {
    return (MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection();
  }
}
