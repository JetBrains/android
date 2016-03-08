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

import com.android.repository.Revision;
import com.android.repository.api.Dependency;
import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.sdklib.repositoryv2.meta.RepoFactory;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;

import java.io.File;
import java.util.Map;

public class AndroidVirtualDeviceTest extends AndroidTestBase {

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
    builder.put("hw.device.name", "Nexus 5X");
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
    // AVD Manager will return a system-dependent path, so the baseline must comply
    String systemImageDir = "system-images/android-23/google_apis/x86/".replace('/', File.separatorChar);
    builder.put("image.sysdir.1", systemImageDir);

    builder.put("runtime.network.latency", "none");
    builder.put("runtime.network.speed", "full");
    builder.put("runtime.scalefactor", "auto");
    builder.put("sdcard.size", "200M");
    builder.put("skin.name", "nexus_5x");
    builder.put("snapshot.present", "no");
    builder.put("tag.display", "Google APIs");
    builder.put("tag.id", "google_apis");
    builder.put("vm.heapSize", "64");
    return builder.build();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCreateDevice() throws Exception {
    MockFileOp fop = new MockFileOp();
    recordPlatform23(fop);
    recordGoogleApisAddon23(fop);
    recordGoogleApisSysImg23(fop);
    fop.recordExistingFile(new File(DeviceArtDescriptor.getBundledDescriptorsFolder(), "nexus_5x"));

    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(new File("/sdk"), fop);

    final AvdManagerConnection connection = new AvdManagerConnection(sdkHandler, fop);
    FakePackage remotePlatform = new FakePackage("platforms;android-23", new Revision(1), ImmutableList.<Dependency>of());
    RepoFactory factory = (RepoFactory)AndroidSdkHandler.getRepositoryModule().createLatestFactory();

    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(23);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);
    Map<String, RemotePackage> remotes = Maps.newHashMap();
    remotes.put("platforms;android-23", remotePlatform);
    AndroidVirtualDevice avd = new AndroidVirtualDevice(new ScopedStateStore(ScopedStateStore.Scope.STEP, null, null), remotes, true, fop);
    final AvdInfo avdInfo = avd.createAvd(connection, sdkHandler);
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
    assertEquals("nexus_5x", skin.getName());
    assertEquals("device-art-resources", skin.getParentFile().getName());
  }

  private static void recordPlatform23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/platforms/android-23/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns2:sdk-repository "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-9A220565\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage path=\"platforms;android-23\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns2:platformDetailsType\"><api-level>23</api-level>"
                           + "<layoutlib api=\"15\"/></type-details><revision><major>1</major>"
                           + "</revision><display-name>API 23: Android 6.0 (Marshmallow)"
                           + "</display-name><uses-license ref=\"license-9A220565\"/><dependencies>"
                           + "<dependency path=\"tools\"><min-revision><major>22</major>"
                           + "</min-revision></dependency></dependencies></localPackage>"
                           + "</ns2:sdk-repository>\n");
    fop.recordExistingFile("/sdk/platforms/android-23/android.jar");
    fop.recordExistingFile("/sdk/platforms/android-23/framework.aidl");
    fop.recordExistingFile("/sdk/platforms/android-23/skins/HVGA/layout");
    fop.recordExistingFile("/sdk/platforms/android-23/skins/dummy.txt");
    fop.recordExistingFile("/sdk/platforms/android-23/skins/WVGA800/layout");
    fop.recordExistingFile("/sdk/platforms/android-23/build.prop",
                           "# autogenerated by buildinfo.sh\n"
                           + "ro.build.id=MRA44C\n"
                           + "ro.build.display.id=sdk_phone_armv7-eng 6.0 MRA44C 2166767 test-keys\n"
                           + "ro.build.version.incremental=2166767\n"
                           + "ro.build.version.sdk=23\n"
                           + "ro.build.version.preview_sdk=0\n"
                           + "ro.build.version.codename=REL\n"
                           + "ro.build.version.all_codenames=REL\n"
                           + "ro.build.version.release=6.0\n"
                           + "ro.build.version.security_patch=\n"
                           + "ro.build.version.base_os=\n"
                           + "ro.build.date=Thu Aug 13 23:46:41 UTC 2015\n"
                           + "ro.build.date.utc=1439509601\n"
                           + "ro.build.type=eng\n"
                           + "ro.build.tags=test-keys\n"
                           + "ro.build.flavor=sdk_phone_armv7-eng\n"
                           + "ro.product.model=sdk_phone_armv7\n"
                           + "ro.product.name=sdk_phone_armv7\n"
                           + "ro.product.board=\n"
                           + "# ro.product.cpu.abi and ro.product.cpu.abi2 are obsolete,\n"
                           + "# use ro.product.cpu.abilist instead.\n"
                           + "ro.product.cpu.abi=armeabi-v7a\n"
                           + "ro.product.cpu.abi2=armeabi\n"
                           + "ro.product.cpu.abilist=armeabi-v7a,armeabi\n"
                           + "ro.product.cpu.abilist32=armeabi-v7a,armeabi\n"
                           + "ro.product.cpu.abilist64=\n"
                           + "ro.product.locale=en-US\n"
                           + "ro.wifi.channels=\n"
                           + "ro.board.platform=\n"
                           + "# ro.build.product is obsolete; use ro.product.device\n"
                           + "# Do not try to parse description, fingerprint, or thumbprint\n"
                           + "ro.build.description=sdk_phone_armv7-eng 6.0 MRA44C 2166767 test-keys\n"
                           + "ro.build.fingerprint=generic/sdk_phone_armv7/generic:6.0/MRA44C/2166767:eng/test-keys\n"
                           + "ro.build.characteristics=default\n"
                           + "# end build properties\n"
                           + "#\n"
                           + "# from build/target/board/generic/system.prop\n"
                           + "#\n"
                           + "#\n"
                           + "# system.prop for generic sdk\n"
                           + "#\n"
                           + "\n"
                           + "rild.libpath=/system/lib/libreference-ril.so\n"
                           + "rild.libargs=-d /dev/ttyS0\n"
                           + "\n"
                           + "#\n"
                           + "# ADDITIONAL_BUILD_PROPERTIES\n"
                           + "#\n"
                           + "ro.config.notification_sound=OnTheHunt.ogg\n"
                           + "ro.config.alarm_alert=Alarm_Classic.ogg\n"
                           + "persist.sys.dalvik.vm.lib.2=libart\n"
                           + "dalvik.vm.isa.arm.variant=generic\n"
                           + "dalvik.vm.isa.arm.features=default\n"
                           + "ro.kernel.android.checkjni=1\n"
                           + "dalvik.vm.lockprof.threshold=500\n"
                           + "dalvik.vm.usejit=true\n"
                           + "xmpp.auto-presence=true\n"
                           + "ro.config.nocheckin=yes\n"
                           + "net.bt.name=Android\n"
                           + "dalvik.vm.stack-trace-file=/data/anr/traces.txt\n"
                           + "ro.build.user=generic\n"
                           + "ro.build.host=generic\n"
                           + "ro.product.brand=generic\n"
                           + "ro.product.manufacturer=generic\n"
                           + "ro.product.device=generic\n"
                           + "ro.build.product=generic\n");
  }

  private static void recordGoogleApisAddon23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/add-ons/addon-google_apis-google-23/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns5:sdk-addon "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-1E15FA4A\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage path=\"add-ons;addon-google_apis-google-23-1\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns5:addonDetailsType\"><api-level>23</api-level><vendor>"
                           + "<id>google</id><display>Google Inc.</display></vendor><tag>"
                           + "<id>google_apis</id><display>Google APIs</display></tag></type-details>"
                           + "<revision><major>1</major><minor>0</minor><micro>0</micro></revision>"
                           + "<display-name>Google APIs, Android 23</display-name><uses-license "
                           + "ref=\"license-1E15FA4A\"/></localPackage></ns5:sdk-addon>\n");
  }

  private static void recordGoogleApisSysImg23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86/system.img", "foo");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86/userdata.img", "bar");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns3:sdk-sys-img "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage "
                           + "path=\"system-images;android-23;google_apis;x86_64\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                           + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                           + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                           + "<abi>x86</abi></type-details><revision><major>9</major></revision>"
                           + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                           + "</ns3:sdk-sys-img>\n");
  }


}