/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.jetbrains.android.dom;

import static com.android.SdkConstants.FN_PERMISSION_VERSIONS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.sdklib.IAndroidTarget;
import org.jetbrains.android.sdk.AndroidPlatforms;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.android.tools.sdk.AndroidPlatform;
import org.jetbrains.android.dom.converters.AndroidPermissionPurposeConverter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Files;
import java.io.File;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

/**
 * Tests for purpose declaration completetion that require a mocked SDK.
 */
public class AndroidManifestPurposeDomTest {
  private static final String DEFAULT_XML_CONTENT =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
    "<permissions>" +
    "    <permission name=\"android.permission.USE_FOO\" requirePurposeMin=\"37\">" +
    "        <valid-purpose name=\"validPurpose1\" min=\"37\"/>" +
    "        <valid-purpose name=\"validPurpose2\" min=\"37\"/>" +
    "    </permission>" +
    "    <permission name=\"android.permission.USE_BAR\" requirePurposeMin=\"37\">" +
    "        <valid-purpose name=\"validPurpose3\" min=\"37\"/>" +
    "    </permission>" +
    "</permissions>";

  private static final String EMPTY_XML_CONTENT =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
    "<permissions>" +
    "</permissions>";

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.withSdk();
  private CodeInsightTestFixture myFixture;

  @Before
  public void setUp() {
    myFixture = projectRule.getFixture();
    AndroidPermissionPurposeConverter.clearCache();
  }

  @Test
  public void testUsesPermissionPurposeCompletion1() throws Exception {
    mockSdkWithXmlFile(DEFAULT_XML_CONTENT);

    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <uses-permission android:name=\"android.permission.USE_FOO\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </uses-permission>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("validPurpose1", "validPurpose2");
  }

  @Test
  public void testUsesPermissionSdk23PurposeCompletion() throws Exception {
    mockSdkWithXmlFile(DEFAULT_XML_CONTENT);

    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <uses-permission-sdk-23 android:name=\"android.permission.USE_FOO\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </uses-permission>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("validPurpose1", "validPurpose2");
  }

  @Test
  public void testUsesPermissionPurposeCompletion2() throws Exception {
    mockSdkWithXmlFile(DEFAULT_XML_CONTENT);

    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <uses-permission android:name=\"android.permission.USE_BAR\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </uses-permission>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).containsExactly("validPurpose3");
  }

  @Test
  public void testPurposeCompletionUnderUnknownTag() throws Exception {
    mockSdkWithXmlFile(DEFAULT_XML_CONTENT);

    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <unknown-tag android:name=\"android.permission.USE_BAR\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </unknown-tag>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).isEmpty();
  }

  @Test
  public void testPurposeCompletionWithEmptyXmlFile() throws Exception {
    mockSdkWithXmlFile(EMPTY_XML_CONTENT);

    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <uses-permission android:name=\"android.permission.USE_BAR\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </uses-permission>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).isEmpty();
  }

  @Test
  public void testPurposeCompletionWithMissingXmlFile() throws Exception {
    myFixture.configureByText("AndroidManifest.xml",
                              "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                              "    <uses-permission android:name=\"android.permission.USE_BAR\">\n" +
                              "        <purpose android:name=\"<caret>\"/>\n" +
                              "    </uses-permission>\n" +
                              "</manifest>");

    myFixture.completeBasic();
    assertThat(myFixture.getLookupElementStrings()).isEmpty();
  }

  private void mockSdkWithXmlFile(String xmlContents) throws Exception {
    AndroidPlatform platform = AndroidPlatforms.getInstance(myFixture.getModule());
    assertNotNull(platform);
    IAndroidTarget target = platform.getTarget();
    assertNotNull(target);

    Path platformDataPath = target.getPath(IAndroidTarget.DATA);
    assertNotNull(platformDataPath);
    File dataDir = new File(platformDataPath.toString());
    dataDir.mkdirs();
    Files.writeString(new File(dataDir, FN_PERMISSION_VERSIONS).toPath(), xmlContents);
  }
}