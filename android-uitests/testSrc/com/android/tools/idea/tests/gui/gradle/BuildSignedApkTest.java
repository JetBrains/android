/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.BuildSignedApkDialogKeystoreStepFixture;
import org.jetbrains.android.exportSignedPackage.GradleSignStep;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

@RunWith(GuiTestRunner.class)
@Ignore("consistently fails with IDE errors that block following tests from running") // b/37560852
public class BuildSignedApkTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Rule public TemporaryFolder myTemporaryFolder = new TemporaryFolder();

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @Ignore("fails with Gradle plugin 2.3.0-dev")
  @Test
  public void openAndSignUsingStore() throws IOException {
    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .clickFinish();

    File[] apks = dstFolder.listFiles();
    assertThat(apks).hasLength(1);
    File apk = apks[0];
    try (ZipFile zf = new ZipFile(apk)) {
      assertThat(zf.getEntry("META-INF/CERT.SF")).isNotNull();
    }
  }

  @Test
  public void openAndSignUsingV1Only() throws IOException {
    GradleVersion latestVersion = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_LATEST_VERSION);
    assume().that(latestVersion.compareIgnoringQualifiers(GradleSignStep.MIN_SIGNATURE_SELECTION_VERSION)).isAtLeast(0);

    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .setV1SignatureEnabled(true)
      .setV2SignatureEnabled(false)
      .clickFinish();

    // We should verify that a V2 signature is not present, but that is hard to do here.
    File[] apks = dstFolder.listFiles();
    assertThat(apks).hasLength(1);
    File apk = apks[0];
    try (ZipFile zf = new ZipFile(apk)) {
      assertThat(zf.getEntry("META-INF/CERT.SF")).isNotNull();
    }
  }

  @Test
  public void openAndSignUsingV2Only() throws IOException {
    GradleVersion latestVersion = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_LATEST_VERSION);
    assume().that(latestVersion.compareIgnoringQualifiers(GradleSignStep.MIN_SIGNATURE_SELECTION_VERSION)).isAtLeast(0);

    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .setV1SignatureEnabled(false)
      .setV2SignatureEnabled(true)
      .clickFinish();

    // We should verify that a V2 signature is present, but that is hard to do here.
    File[] apks = dstFolder.listFiles();
    assertThat(apks).hasLength(1);
    File apk = apks[0];
    try (ZipFile zf = new ZipFile(apk)) {
      assertThat(zf.getEntry("META-INF/CERT.SF")).isNull();
    }
  }

  @Test
  public void openAndSignUsingV1AndV2() throws IOException {
    GradleVersion latestVersion = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_LATEST_VERSION);
    assume().that(latestVersion.compareIgnoringQualifiers(GradleSignStep.MIN_SIGNATURE_SELECTION_VERSION)).isAtLeast(0);

    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .setV1SignatureEnabled(true)
      .setV2SignatureEnabled(true)
      .clickFinish();

    // We should verify that a V2 signature is present, but that is hard to do here.
    File[] apks = dstFolder.listFiles();
    assertThat(apks).hasLength(1);
    File apk = apks[0];
    try (ZipFile zf = new ZipFile(apk)) {
      assertThat(zf.getEntry("META-INF/CERT.SF")).isNotNull();
    }
  }

  @Test
  public void mustHaveAtLeastV1OrV2Sign() throws IOException {
    GradleVersion latestVersion = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_LATEST_VERSION);
    assume().that(latestVersion.compareIgnoringQualifiers(GradleSignStep.MIN_SIGNATURE_SELECTION_VERSION)).isAtLeast(0);

    File jksFile = new File(myTemporaryFolder.getRoot(), "jks");
    File dstFolder = myTemporaryFolder.newFolder("dst");

    guiTest.importSimpleApplication()
      .openFromMenu(BuildSignedApkDialogKeystoreStepFixture::find, "Build", "Generate Signed APK...")
      .createNew()
      .keyStorePath(jksFile.getAbsolutePath())
      .password("passwd")
      .passwordConfirm("passwd")
      .alias("key")
      .keyPassword("passwd2")
      .keyPasswordConfirm("passwd2")
      .validity("3")
      .firstAndLastName("Android Studio")
      .organizationalUnit("Android")
      .organization("Google")
      .cityOrLocality("Mountain View")
      .stateOrProvince("California")
      .countryCode("US")
      .clickOk()
      .keyStorePassword("passwd")
      .keyAlias("key")
      .keyPassword("passwd2")
      .clickNext()
      .apkDestinationFolder(dstFolder.getAbsolutePath())
      .setV1SignatureEnabled(false)
      .setV2SignatureEnabled(false)
      .clickFinishAndDismissErrorDialog()
      .setV1SignatureEnabled(true)
      .clickFinish();
  }
}
