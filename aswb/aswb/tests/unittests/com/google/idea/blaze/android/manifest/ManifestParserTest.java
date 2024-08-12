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
package com.google.idea.blaze.android.manifest;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.intellij.lang.annotations.Language;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.idea.blaze.android.manifest.ManifestParser}. */
@RunWith(JUnit4.class)
public class ManifestParserTest {

  /**
   * This is an example merged manifest from the instrumentation APK for hellogoogle3 project. It
   * contains a package name, two instrumentation classes, but no default activity (because it's an
   * instrumentation APK, those don't have default activities).
   */
  @Language("XML")
  public static final String TEST_MANIFEST_XML_CONTENTS =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest\n"
          + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    android:versionCode=\"1\"\n"
          + "    android:versionName=\"1.0\"\n"
          + "    android:compileSdkVersion=\"29\"\n"
          + "    android:compileSdkVersionCodename=\"10\"\n"
          + "    package=\"com.google.android.samples.hellogoogle3.test\"\n"
          + "    platformBuildVersionCode=\"29\"\n"
          + "    platformBuildVersionName=\"10\">\n"
          + "  <uses-sdk\n"
          + "      android:minSdkVersion=\"7\"\n"
          + "      android:targetSdkVersion=\"28\" />\n"
          + "  <instrumentation\n"
          + "     "
          + " android:name=\"com.google.android.apps.common.testing.testrunner.Google3InstrumentationTestRunner\"\n"
          + "      android:targetPackage=\"com.google.android.samples.hellogoogle3\" />\n"
          + "  <instrumentation\n"
          + "      android:name=\"androidx.test.runner.AndroidJUnitRunner\"\n"
          + "      android:targetPackage=\"com.google.android.samples.hellogoogle3\" />\n"
          + "  <application\n"
          + "      android:label=\"SampleTest\"\n"
          + "      android:debuggable=\"true\">\n"
          + "    <uses-library\n"
          + "        android:name=\"android.test.runner\" />\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040015\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.AddActivity\""
          + " />\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f04001a\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.SubtractActivity\""
          + " />\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040016\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.DisplayMessageActivity\""
          + " />\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.MessageServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action\n"
          + "           "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.MessageService\""
          + " />\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.AdditionServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action\n"
          + "           "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.AdditionService\""
          + " />\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.SubtractionServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action\n"
          + "           "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.SubtractionService\""
          + " />\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "  </application>\n"
          + "</manifest>";

  /**
   * This is an example merged manifest from the app APK for hellogoogle3 project. It contains a
   * package name and a default activity.
   */
  @Language("XML")
  public static final String APP_MANIFEST_XML_CONTENTS =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest\n"
          + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    android:versionCode=\"1\"\n"
          + "    android:versionName=\"1.0\"\n"
          + "    android:compileSdkVersion=\"29\"\n"
          + "    android:compileSdkVersionCodename=\"10\"\n"
          + "    package=\"com.google.android.samples.hellogoogle3\"\n"
          + "    platformBuildVersionCode=\"29\"\n"
          + "    platformBuildVersionName=\"10\">\n"
          + "  <uses-sdk\n"
          + "      android:minSdkVersion=\"7\"\n"
          + "      android:targetSdkVersion=\"28\"/>\n"
          + "  <uses-permission\n"
          + "      android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n"
          + "  <application\n"
          + "      android:label=\"@ref/0x7f040000\"\n"
          + "      android:icon=\"@ref/0x7f010000\"\n"
          + "      android:debuggable=\"true\">\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040018\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.activities.MainActivity\">\n"
          + "      <intent-filter>\n"
          + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
          + "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
          + "      </intent-filter>\n"
          + "    </activity>\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040019\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.activities.MultiplyActivity\"/>\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040017\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.activities.DivideActivity\"/>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.service.DivisionServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action\n"
          + "           "
          + " android:name=\"com.google.android.samples.hellogoogle3.service.DivisionService\"/>\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.service.MultiplicationServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action"
          + " android:name=\"com.google.android.samples.hellogoogle3.service.MultiplicationService\"/>\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040015\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.AddActivity\"/>\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f04001a\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.SubtractActivity\"/>\n"
          + "    <activity\n"
          + "        android:label=\"@ref/0x7f040016\"\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.activities.DisplayMessageActivity\"/>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.MessageServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action"
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.MessageService\"/>\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.AdditionServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action"
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.AdditionService\"/>\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "    <service\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.SubtractionServer\"\n"
          + "        android:enabled=\"true\"\n"
          + "        android:exported=\"true\">\n"
          + "      <intent-filter>\n"
          + "        <action"
          + " android:name=\"com.google.android.samples.hellogoogle3lib.service.SubtractionService\"/>\n"
          + "      </intent-filter>\n"
          + "    </service>\n"
          + "  </application>\n"
          + "</manifest>";

  /**
   * This is an example partial manifest with an empty package name that only defines a startup
   * activity. Manifests like these aren't final like merged manifests but we should still extract
   * what we can from it (in this case the startup activity).
   */
  @Language("XML")
  public static final String PARTIAL_MANIFEST_XML_CONTENTS =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest\n"
          + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    package=\"\">\n"
          + "  <application>\n"
          + "    <activity\n"
          + "       "
          + " android:name=\"com.google.android.samples.hellogoogle3.activities.MainActivity\">\n"
          + "      <intent-filter>\n"
          + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
          + "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
          + "      </intent-filter>\n"
          + "    </activity>\n"
          + "  </application>\n"
          + "</manifest>";

  @Test
  public void extractTestAttributes() throws Exception {
    InputStream manifestInputStream =
        new ByteArrayInputStream(TEST_MANIFEST_XML_CONTENTS.getBytes(UTF_8));

    ManifestParser.ParsedManifest parsedManifest =
        ManifestParser.parseManifestFromInputStream(manifestInputStream);
    assertThat(parsedManifest).isNotNull();
    assertThat(parsedManifest.packageName)
        .isEqualTo("com.google.android.samples.hellogoogle3.test");
    assertThat(parsedManifest.defaultActivityClassName).isNull();
    assertThat(parsedManifest.instrumentationClassNames)
        .containsExactly(
            "com.google.android.apps.common.testing.testrunner.Google3InstrumentationTestRunner",
            "androidx.test.runner.AndroidJUnitRunner");
  }

  @Test
  public void extractAppAttributes() throws Exception {
    InputStream manifestInputStream =
        new ByteArrayInputStream(APP_MANIFEST_XML_CONTENTS.getBytes(UTF_8));

    ManifestParser.ParsedManifest parsedManifest =
        ManifestParser.parseManifestFromInputStream(manifestInputStream);
    assertThat(parsedManifest).isNotNull();
    assertThat(parsedManifest.packageName).isEqualTo("com.google.android.samples.hellogoogle3");
    assertThat(parsedManifest.defaultActivityClassName)
        .isEqualTo("com.google.android.samples.hellogoogle3.activities.MainActivity");
    assertThat(parsedManifest.instrumentationClassNames).isEmpty();
  }

  @Test
  public void extractPartialAttributes() throws Exception {
    InputStream manifestInputStream =
        new ByteArrayInputStream(PARTIAL_MANIFEST_XML_CONTENTS.getBytes(UTF_8));

    ManifestParser.ParsedManifest parsedManifest =
        ManifestParser.parseManifestFromInputStream(manifestInputStream);
    assertThat(parsedManifest).isNotNull();
    assertThat(parsedManifest.packageName).isNull();
    assertThat(parsedManifest.defaultActivityClassName)
        .isEqualTo("com.google.android.samples.hellogoogle3.activities.MainActivity");
    assertThat(parsedManifest.instrumentationClassNames).isEmpty();
  }

  @Test
  public void extractFromInvalidManifestShouldYieldNull() throws Exception {
    InputStream manifestInputStream = new ByteArrayInputStream("hello world".getBytes(UTF_8));

    ManifestParser.ParsedManifest parsedManifest =
        ManifestParser.parseManifestFromInputStream(manifestInputStream);
    assertThat(parsedManifest).isNull();
  }
}
