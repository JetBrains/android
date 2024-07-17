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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.prefs.AndroidLocationsSingletonRule;
import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakePackage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.ConfigKey;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.RepoFactory;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.TemporaryDirectoryRule;
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RuleChain;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for {@link AndroidVirtualDevice}. */
@RunsInEdt
public final class AndroidVirtualDeviceTest {
  private static final String DEVICE_SKIN =  "1080x2400";

  private static Map<String, String> getReferenceMap() {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("AvdId", "Medium_Phone_API_34");
    builder.put("PlayStore.enabled", "true");
    builder.put("abi.type", "x86_64");
    builder.put("avd.ini.displayname", "Medium Phone API 34");
    builder.put("avd.ini.encoding", "UTF-8");
    builder.put("disk.dataPartition.size", "2G");
    builder.put("hw.accelerometer", "yes");
    builder.put("hw.arc", "false");
    builder.put("hw.audioInput", "yes");
    builder.put("hw.battery", "yes");
    builder.put("hw.camera.back", "virtualscene");
    builder.put("hw.camera.front", "emulated");
    builder.put("hw.cpu.arch", "x86_64");
    builder.put("hw.dPad", "no");
    builder.put("hw.device.hash2", "MD5:3db3250dab5d0d93b29353040181c7e9");
    builder.put("hw.device.manufacturer", "Generic");
    builder.put("hw.device.name", "medium_phone");
    builder.put("hw.gps", "yes");
    builder.put("hw.gpu.enabled", "yes");
    builder.put("hw.gpu.mode", "auto");
    builder.put("hw.keyboard", "yes");
    builder.put("hw.lcd.density", "420");
    builder.put("hw.lcd.height", "2400");
    builder.put("hw.lcd.width", "1080");
    builder.put("hw.mainKeys", "no");
    builder.put("hw.ramSize", "2G");
    builder.put("hw.sdCard", "yes");
    builder.put("hw.sensors.orientation", "yes");
    builder.put("hw.sensors.proximity", "yes");
    builder.put("hw.trackBall", "no");
    builder.put("image.sysdir.1", "system-images/android-34/google_apis_playstore/x86_64/");
    builder.put("runtime.network.latency", "none");
    builder.put("runtime.network.speed", "full");
    builder.put("sdcard.size", "800M");
    builder.put("skin.name", DEVICE_SKIN);
    builder.put("tag.display", "Google Play");
    builder.put("tag.displaynames", "Google Play");
    builder.put("tag.id", "google_apis_playstore");
    builder.put("tag.ids", "google_apis_playstore");
    builder.put("vm.heapSize", "336");

    return builder.build();
  }

  private final Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");

  public final TemporaryDirectoryRule tempDirectoryRule = new TemporaryDirectoryRule();
  public final AndroidLocationsSingletonRule environmentRule = new AndroidLocationsSingletonRule(sdkRoot.getFileSystem());
  public final DisposableRule disposableRule = new DisposableRule();
  public final ApplicationRule applicationRule = new ApplicationRule();

  @Rule
  public RuleChain ruleChain = new RuleChain(applicationRule, environmentRule, tempDirectoryRule, new EdtRule(), disposableRule);

  private AndroidSdkHandler sdkHandler;
  ProgressStep progressStep;

  @Before
  public void setUp() throws Exception {
    recordPlatform(sdkRoot);
    recordGoogleApisAddon(sdkRoot);
    recordGoogleApisSysImg(sdkRoot);
    sdkHandler = new AndroidSdkHandler(sdkRoot, sdkRoot.getRoot().resolve("android-home"));

    IdeSdks ideSdks = spy(IdeSdks.getInstance());
    when(ideSdks.getAndroidSdkPath()).thenReturn(FileOpUtils.toFile(sdkRoot));
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), IdeSdks.class, ideSdks, disposableRule.getDisposable());
    AndroidSdks androidSdks = spy(AndroidSdks.getInstance());
    when(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), AndroidSdks.class, androidSdks, disposableRule.getDisposable());
    progressStep = new ProgressStep(disposableRule.getDisposable(), "test") {
      @Override
      protected void execute() {}
    };
  }

  @Test
  public void testCreateAvd() throws Exception {
    FakePackage.FakeRemotePackage remotePlatform = new FakePackage.FakeRemotePackage("platforms;android-34");
    RepoFactory factory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();

    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(34);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);
    Map<String, RemotePackage> remotes = new HashMap<>();
    remotes.put("platforms;android-34", remotePlatform);
    AndroidVirtualDevice avdCreator = new AndroidVirtualDevice(remotes, true);
    AvdInfo avdInfo = createAvdIfNeeded(avdCreator, sdkHandler);
    assertThat(avdInfo).isNotNull();
    Map<String, String> properties = avdInfo.getProperties();
    Map<String, String> referenceMap = getReferenceMap();
    for (Map.Entry<String, String> entry : referenceMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      String property = properties.get(key);
      if (property == null) {
        fail("\"" + key + "\" property is missing");
      }
      property = FileUtil.toSystemIndependentName(property);
      if (!property.equals(value)) {
        fail("\"" + key + "\" property is \"" + property + "\" instead of expected \"" + value + "\"");
      }
    }
    // AVD manager will set some extra properties that we don't care about and that may be system dependant.
    // We do not care about those, so we only ensure we have the ones we need.
    File skin = new File(properties.get(ConfigKey.SKIN_PATH));
    assertEquals(DEVICE_SKIN, skin.getName());
  }

  @Test
  public void testNoAvdIsCreatedIfThereAreExistingOnes() throws Exception {
    createPlaceholderAvd();

    FakePackage.FakeRemotePackage remotePlatform = new FakePackage.FakeRemotePackage("platforms;android-34");
    RepoFactory factory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();

    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(34);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);
    Map<String, RemotePackage> remotes = new HashMap<>();
    remotes.put("platforms;android-34", remotePlatform);
    AndroidVirtualDevice avdCreator = new AndroidVirtualDevice(remotes, true);
    AvdInfo avdInfo = createAvdIfNeeded(avdCreator, sdkHandler);
    assertThat(avdInfo).isNull();
  }

  @Test
  public void testRequiredSysimgPath() {
    FakePackage.FakeRemotePackage remotePlatform = new FakePackage.FakeRemotePackage("platforms;android-34");
    RepoFactory factory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();

    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(34);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);

    Map<String, RemotePackage> remotes = new HashMap<>();
    remotes.put("platforms;android-34", remotePlatform);

    AndroidVirtualDevice avd = new AndroidVirtualDevice(remotes, true);
    avd.sdkHandler = sdkHandler;

    assertEquals("system-images;android-34;google_apis_playstore;x86_64", avd.getRequiredSysimgPath(false));
    assertEquals("system-images;android-34;google_apis_playstore;arm64-v8a", avd.getRequiredSysimgPath(true));
  }

  @Test
  public void testSysimgWithExtensionLevel() {
    // create remote packages for both a base platform and for an extension of the same platform.
    RepoFactory factory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();
    Map<String, RemotePackage> remotes = new HashMap<>();

    // Base: API 33
    FakePackage.FakeRemotePackage baseRemotePlatform = new FakePackage.FakeRemotePackage("platforms;android-33");
    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(33);
    baseRemotePlatform.setTypeDetails((TypeDetails)platformDetailsType);
    remotes.put("platforms;android-33", baseRemotePlatform);

    // Extension: API 33, extension 3.
    FakePackage.FakeRemotePackage remotePlatform = new FakePackage.FakeRemotePackage("platforms;android-33-ext3");
    platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(33);
    platformDetailsType.setBaseExtension(false);
    platformDetailsType.setExtensionLevel(3);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);
    remotes.put("platforms;android-33-ext3", remotePlatform);

    AndroidVirtualDevice avd = new AndroidVirtualDevice(remotes, true);
    avd.sdkHandler = sdkHandler;

    // The selected image should be the base one.
    assertEquals("system-images;android-33;google_apis_playstore;x86_64", avd.getRequiredSysimgPath(false));
    assertEquals("system-images;android-33;google_apis_playstore;arm64-v8a", avd.getRequiredSysimgPath(true));
  }

  @Test
  public void testSelectedByDefault() throws Exception {
    FakePackage.FakeRemotePackage remotePlatform = new FakePackage.FakeRemotePackage("platforms;android-34");
    RepoFactory factory = AndroidSdkHandler.getRepositoryModule().createLatestFactory();

    DetailsTypes.PlatformDetailsType platformDetailsType = factory.createPlatformDetailsType();
    platformDetailsType.setApiLevel(34);
    remotePlatform.setTypeDetails((TypeDetails)platformDetailsType);

    Map<String, RemotePackage> remotes = Maps.newHashMap();

    AndroidVirtualDevice avd = new AndroidVirtualDevice(remotes, true);

    // No SDK installed -> Not selected by default
    assertFalse(avd.isSelectedByDefault());

    // SDK installed, but no system image -> Selected by default
    avd.sdkHandler = sdkHandler;
    assertTrue(avd.isSelectedByDefault());

    // SDK installed, System image, but no AVD -> Selected by default
    remotes.put("platforms;android-34", remotePlatform);
    avd = new AndroidVirtualDevice(remotes, true);
    avd.sdkHandler = sdkHandler;
    assertTrue(avd.isSelectedByDefault());

    // SDK installed, System image, matching AVD -> Not selected by default
    createAvdIfNeeded(avd, sdkHandler);

    assertFalse(avd.isSelectedByDefault());
  }

  private static void recordPlatform(Path sdkRoot) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns2:sdk-repository "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-9A220565\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage path=\"platforms;android-34\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns2:platformDetailsType\"><api-level>34</api-level>"
                           + "<layoutlib api=\"34\"/></type-details><revision><major>1</major>"
                           + "</revision><display-name>API 34: Android 14 (UpsideDownCake)"
                           + "</display-name><uses-license ref=\"license-9A220565\"/><dependencies>"
                           + "<dependency path=\"tools\"><min-revision><major>34</major>"
                           + "</min-revision></dependency></dependencies></localPackage>"
                           + "</ns2:sdk-repository>\n");
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/android.jar"));
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/framework.aidl"));
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/skins/HVGA/layout"));
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/skins/WVGA800/layout"));
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-34/build.prop"),
                           "# autogenerated by buildinfo.sh\n"
                           + "ro.build.id=MRA44C\n"
                           + "ro.build.display.id=sdk_phone_armv7-eng 6.0 MRA44C 2166767 test-keys\n"
                           + "ro.build.version.incremental=2166767\n"
                           + "ro.build.version.sdk=34\n"
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

  private static void recordGoogleApisAddon(Path sdkRoot) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("add-ons/addon-google_apis-google-34/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns5:sdk-addon "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-1E15FA4A\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage path=\"add-ons;addon-google_apis-google-34-1\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns5:addonDetailsType\"><api-level>34</api-level><vendor>"
                           + "<id>google</id><display>Google Inc.</display></vendor><tag>"
                           + "<id>google_apis_playstore</id><display>Google APIs</display></tag></type-details>"
                           + "<revision><major>1</major><minor>0</minor><micro>0</micro></revision>"
                           + "<display-name>Google APIs, Android 14</display-name><uses-license "
                           + "ref=\"license-1E15FA4A\"/></localPackage></ns5:sdk-addon>\n");
  }

  private static void recordGoogleApisSysImg(Path sdkRoot) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-34/google_apis_playstore/x86_64/system.img"), "foo");
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-34/google_apis_playstore/x86_64/userdata.img"), "bar");
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-34/google_apis_playstore/x86_64/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns3:sdk-sys-img "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage "
                           + "path=\"system-images;android-34;google_apis_playstore;x86_64\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>34</api-level>"
                           + "<tag><id>google_apis_playstore</id><display>Google Play</display></tag>"
                           + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                           + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                           + "<display-name>Google APIs Intel x86_64 Atom_64 System Image</display-name>"
                           + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                           + "</ns3:sdk-sys-img>\n");
  }

  private @Nullable AvdInfo createAvdIfNeeded(
      @NotNull AndroidVirtualDevice avdCreator, @NotNull AndroidSdkHandler sdkHandler) throws Exception {
    if (!avdCreator.isAvdCreationNeeded(sdkHandler)) {
      return null;
    }
    Path avdFolder = AndroidLocationsSingleton.INSTANCE.getAvdLocation();
    AvdManagerConnection connection = new AvdManagerConnection(sdkHandler, avdFolder, MoreExecutors.newDirectExecutorService());
    Set<AvdInfo> existingAvds = new HashSet<>(connection.getAvds(true));
    avdCreator.init(progressStep);
    InstallContext context = new InstallContext(tempDirectoryRule.newPath().toFile(), progressStep);
    avdCreator.configure(context, sdkHandler);
    List<AvdInfo> newAvds = connection.getAvds(true);
    for (AvdInfo avd : newAvds) {
      if (!existingAvds.contains(avd)) {
        return avd;
      }
    }
    return null;
  }

  private void createPlaceholderAvd() throws Exception {
    String avdId = "Pixel_3_XL_API_34";
    Path parentDir = AndroidLocationsSingleton.INSTANCE.getAvdLocation();
    Path avdFolder = parentDir.resolve(avdId + ".avd");

    NioFiles.createDirectories(avdFolder);
    Files.writeString(avdFolder.resolve("config.ini"), "AvdId=" + avdId);
    Files.writeString(avdFolder.resolve("hardware-qemu.ini"), "");
    Files.writeString(avdFolder.getParent().resolve(avdId + ".ini"), "path=" + avdFolder);
  }
}
