/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import java.io.File;
import java.util.Map;

public class AndroidVirtualDeviceTest extends AndroidTestBase {
  private static boolean DISABLED = true; // Don't know if all required components are installed on a test farm.

  private File myAndroidSdkPath;

  private static Map<String, String> getReferenceMap() {
    // Expected values are defined in http://b.android.com/78945
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("abi.type", "x86");
    builder.put("disk.dataPartition.size", "200M");
    builder.put("hw.accelerometer", "yes");
    builder.put("hw.audioInput", "yes");
    builder.put("hw.battery", "yes");
    builder.put("hw.camera.back", "emulated");
    builder.put("hw.camera.front", "emulated");
    builder.put("hw.cpu.arch", "x86");
    builder.put("hw.dPad", "no");
    builder.put("hw.device.manufacturer", "Google");
    builder.put("hw.device.name", "Nexus 5");
    builder.put("hw.gps", "yes");
    builder.put("hw.gpu.enabled", "yes");
    builder.put("hw.keyboard", "yes");
    builder.put("hw.lcd.density", "480");
    builder.put("hw.mainKeys", "no");
    builder.put("hw.ramSize", "1536");
    builder.put("hw.sdCard", "yes");
    builder.put("hw.sensors.orientation", "yes");
    builder.put("hw.sensors.proximity", "yes");
    builder.put("hw.trackBall", "no");
    builder.put("image.sysdir.1", "system-images/android-21/google_apis/x86/");
    builder.put("runtime.network.latency", "none");
    builder.put("runtime.network.speed", "full");
    builder.put("runtime.scalefactor", "auto");
    builder.put("sdcard.size", "200M");
    builder.put("skin.dynamic", "no");
    builder.put("skin.name", "nexus_5");
    builder.put("snapshot.present", "no");
    builder.put("tag.display", "Google APIs");
    builder.put("tag.id", "google_apis");
    builder.put("vm.heapSize", "64");
    return builder.build();
  }

  @Override
  public void setUp() throws Exception {
    if (DISABLED) {
      return; // Disabled
    }
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myAndroidSdkPath = AndroidTestCaseHelper.getAndroidSdkPath();
    AndroidSdkUtils.createNewAndroidPlatform(myAndroidSdkPath.getAbsolutePath(), false);
  }

  @Override
  protected void tearDown() throws Exception {
    if (DISABLED) {
      return; // Disabled
    }
    myFixture.tearDown();
    super.tearDown();
  }

  public void testCreateDevice() throws WizardException {
    if (DISABLED) {
      return; // Disabled
    }
    final AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    SdkManager manager = SdkManager.createManager(myAndroidSdkPath.getAbsolutePath(), new StdLogger(StdLogger.Level.VERBOSE));
    assertNotNull(manager);
    LocalSdk localSdk = manager.getLocalSdk();
    AndroidVirtualDevice avd = new AndroidVirtualDevice(new ScopedStateStore(ScopedStateStore.Scope.STEP, null, null), null);
    final AvdInfo avdInfo = avd.createAvd(connection, localSdk);
    assertNotNull(avdInfo);
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        connection.deleteAvd(avdInfo);
      }
    });
    assertNotNull(avdInfo);
    Map<String, String> properties = avdInfo.getProperties();
    Map<String, String> referenceMap = getReferenceMap();
    for (Map.Entry<String, String> entry : referenceMap.entrySet()) {
      assertEquals(entry.getKey(), entry.getValue(), properties.get(entry.getKey()));
    }
    // AVD manager will set some extra properties that we don't care about and that may be system dependant.
    // We do not care about those so we only ensure we have the ones we need.
    File skin = new File(properties.get(AvdManager.AVD_INI_SKIN_PATH));
    assertEquals("nexus_5", skin.getName());
    assertEquals("device-art-resources", skin.getParentFile().getName());
  }
}