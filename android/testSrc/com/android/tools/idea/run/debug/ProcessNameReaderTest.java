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
package com.android.tools.idea.run.debug;

import static org.mockito.Mockito.mock;

import com.android.SdkConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.Nullable;

public class ProcessNameReaderTest extends LightIdeaTestCase {

  AndroidFacet myFacet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFacet = new AndroidFacet(getModule(), "app", mock(AndroidFacetConfiguration.class));
  }


  public void testHasGlobalProcess_HasNoManifest() {
    mockManifest(null);
    assertFalse(ProcessNameReader.INSTANCE.hasGlobalProcess(myFacet, "processname"));
  }

  public void testHasGlobalProcess_HasNoXml() {
    mockManifest(createFile(SdkConstants.FN_ANDROID_MANIFEST_XML, "").getVirtualFile());
    assertFalse(ProcessNameReader.INSTANCE.hasGlobalProcess(myFacet, "processname"));
  }

  public void testHasGlobalProcess() {
    PsiFile manifest = createFile(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
      "    package=\"com.example.buildvariantpaneltest\">\n" +
      "\n" +
      "    <application\n" +
      "        android:allowBackup=\"true\"\n" +
      "        android:icon=\"@mipmap/ic_launcher\"\n" +
      "        android:label=\"@string/app_name\"\n" +
      "        android:roundIcon=\"@mipmap/ic_launcher_round\"\n" +
      "        android:supportsRtl=\"true\"\n" +
      "        android:theme=\"@style/AppTheme\"\n" +
      "        tools:ignore=\"GoogleAppIndexingWarning\">\n" +
      "\n" +
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process=\"com.example.globalprocess\">\n" +
      "            <intent-filter>\n" +
      "                <action android:name=\"android.intent.action.MAIN\" />\n" +
      "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
      "            </intent-filter>\n" +
      "        </activity>\n" +
      "\n" +
      "        <activity\n" +
      "            android:name=\".Main2Activity\"\n" +
      "            android:process=\":localprocess\" />\n" +
      "\n" +
      "        <activity\n" +
      "            android:name=\".Main3Activity\" />\n" +
      "\n");
    mockManifest(manifest.getVirtualFile());

    assertTrue(ProcessNameReader.INSTANCE.hasGlobalProcess(myFacet, "com.example.globalprocess"));
    assertFalse(ProcessNameReader.INSTANCE.hasGlobalProcess(myFacet, "com.example.anotherprocess"));
    assertFalse(ProcessNameReader.INSTANCE.hasGlobalProcess(myFacet, ":localprocess"));
  }

  private void mockManifest(@Nullable VirtualFile manifestFile) {
    SourceProviderManager.replaceForTest(myFacet, getTestRootDisposable(), manifestFile);
  }
}