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
package com.android.tools.idea.sdk;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.util.EnumSet;

public class SdkMergerTest extends AndroidTestCase {
  private static final Logger LOG = Logger.getInstance(SdkMergerTest.class);
  public static final String SDK_20_FINGERPRINT = "tools,23.0.2,null\n" +
                                                  "platform-tools,20.0.0,null\n" +
                                                  "build-tools-20.0.0,20.0.0,null\n" +
                                                  "doc,null,1\n" +
                                                  "android-19,null,2\n" +
                                                  "android-20,null,2\n";
  public static final String SDK_L_FINGERPRINT = "tools,23.0.2,null\n" +
                                                 "platform-tools,20.0.0,null\n" +
                                                 "build-tools-18.1.1,18.1.1,null\n" +
                                                 "build-tools-19.0.0,19.0.0,null\n" +
                                                 "build-tools-19.0.1,19.0.1,null\n" +
                                                 "build-tools-19.0.2,19.0.2,null\n" +
                                                 "build-tools-19.0.3,19.0.3,null\n" +
                                                 "build-tools-19.1.0,19.1.0,null\n" +
                                                 "build-tools-20.0.0,20.0.0,null\n" +
                                                 "android-8,null,3\n" +
                                                 "android-10,null,2\n" +
                                                 "android-14,null,3\n" +
                                                 "android-15,null,3\n" +
                                                 "android-16,null,4\n" +
                                                 "android-17,null,2\n" +
                                                 "android-18,null,2\n" +
                                                 "android-19,null,3\n" +
                                                 "android-20,null,1\n" +
                                                 "android-l,null,4\n" +
                                                 "addon-google_gdk-google-19,null,8\n" +
                                                 "extra-android-m2repository,6.0.0,null\n" +
                                                 "extra-android-support,20.0.0,null\n" +
                                                 "extra-google-m2repository,11.0.0,null\n";
  public static final String MERGED_FINGERPRINT = "tools,23.0.2,null\n" +
                                                  "platform-tools,20.0.0,null\n" +
                                                  "build-tools-18.1.1,18.1.1,null\n" +
                                                  "build-tools-19.0.0,19.0.0,null\n" +
                                                  "build-tools-19.0.1,19.0.1,null\n" +
                                                  "build-tools-19.0.2,19.0.2,null\n" +
                                                  "build-tools-19.0.3,19.0.3,null\n" +
                                                  "build-tools-19.1.0,19.1.0,null\n" +
                                                  "build-tools-20.0.0,20.0.0,null\n" +
                                                  "doc,null,1\n" +
                                                  "android-8,null,3\n" +
                                                  "android-10,null,2\n" +
                                                  "android-14,null,3\n" +
                                                  "android-15,null,3\n" +
                                                  "android-16,null,4\n" +
                                                  "android-17,null,2\n" +
                                                  "android-18,null,2\n" +
                                                  "android-19,null,3\n" +
                                                  "android-20,null,2\n" +
                                                  "android-l,null,4\n" +
                                                  "addon-google_gdk-google-19,null,8\n" +
                                                  "extra-android-m2repository,6.0.0,null\n" +
                                                  "extra-android-support,20.0.0,null\n" +
                                                  "extra-google-m2repository,11.0.0,null\n";

  public void testMerge() throws Exception {
    String tempDirPath = myFixture.getTempDirPath();
    File newSdk = new File(tempDirPath, "dest-sdk");
    FileUtil.copyDir(new File(getTestDataPath(), "sdkL-stub"), newSdk);
    File oldSdk = new File(getTestDataPath(), "sdk20-stub");

    assertEquals(SDK_20_FINGERPRINT,
                 getSdkFingerprint(oldSdk));
    assertEquals(SDK_L_FINGERPRINT,
                 getSdkFingerprint(newSdk));
    assertTrue(SdkMerger.hasMergeableContent(oldSdk, newSdk));
    SdkMerger.mergeSdks(oldSdk, newSdk, null);
    assertFalse(SdkMerger.hasMergeableContent(oldSdk, newSdk));
    assertEquals(MERGED_FINGERPRINT, getSdkFingerprint(newSdk));
    assertTrue(new File(newSdk, "docs/favicon.ico").exists());
  }

  public void testReverseMerge() throws Exception {
    String tempDirPath = myFixture.getTempDirPath();
    File newSdk = new File(tempDirPath, "dest-sdk");
    FileUtil.copyDir(new File(getTestDataPath(), "sdk20-stub"), newSdk);
    File oldSdk = new File(getTestDataPath(), "sdkL-stub");

    assertEquals(SDK_L_FINGERPRINT,
                 getSdkFingerprint(oldSdk));
    assertEquals(SDK_20_FINGERPRINT,
                 getSdkFingerprint(newSdk));
    assertTrue(SdkMerger.hasMergeableContent(oldSdk, newSdk));
    SdkMerger.mergeSdks(oldSdk, newSdk, null);
    assertFalse(SdkMerger.hasMergeableContent(oldSdk, newSdk));
    assertEquals(MERGED_FINGERPRINT, getSdkFingerprint(newSdk));
    assertTrue(new File(newSdk, "platforms/android-8/data/activity_actions.txt").exists());
    assertTrue(new File(newSdk, "platforms/android-15/data/activity_actions.txt").exists());

  }

  private String getSdkFingerprint(File sdk) {
    ILogger logger = new LogWrapper(LOG);
    StringBuilder s = new StringBuilder();
    for (LocalPkgInfo pkg : SdkManager.createManager(sdk.getPath(), logger).getLocalSdk().getPkgsInfos(EnumSet.allOf(PkgType.class))) {
      IPkgDesc desc = pkg.getDesc();
      s.append(desc.getInstallId());
      s.append(',');
      s.append(desc.getFullRevision());
      s.append(',');
      s.append(desc.getMajorRevision());
      s.append('\n');
    }
    return s.toString();
  }
}
