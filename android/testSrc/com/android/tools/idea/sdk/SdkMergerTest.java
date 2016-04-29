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

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;

public class SdkMergerTest extends AndroidTestCase {
  public static final String SDK_20_FINGERPRINT = "build-tools;20.0.0,20.0.0\n" +
                                                  "docs,1\n" +
                                                  "platform-tools,20.0.0\n" +
                                                  "platforms;android-19,2\n" +
                                                  "platforms;android-20,2\n" +
                                                  "tools,23.0.2\n";
  public static final String SDK_L_FINGERPRINT = "add-ons;addon-google_gdk-google-19,8.0.0\n" +
                                                 "build-tools;18.1.1,18.1.1\n" +
                                                 "build-tools;19.0.0,19.0.0\n" +
                                                 "build-tools;19.0.1,19.0.1\n" +
                                                 "build-tools;19.0.2,19.0.2\n" +
                                                 "build-tools;19.0.3,19.0.3\n" +
                                                 "build-tools;19.1.0,19.1.0\n" +
                                                 "build-tools;20.0.0,20.0.0\n" +
                                                 "extras;android;m2repository,6.0.0\n" +
                                                 "extras;android;support,20.0.0\n" +
                                                 "extras;google;m2repository,11.0.0\n" +
                                                 "platform-tools,20.0.0\n" +
                                                 "platforms;android-10,2\n" +
                                                 "platforms;android-14,3\n" +
                                                 "platforms;android-15,3\n" +
                                                 "platforms;android-16,4\n" +
                                                 "platforms;android-17,2\n" +
                                                 "platforms;android-18,2\n" +
                                                 "platforms;android-19,3\n" +
                                                 "platforms;android-20,1\n" +
                                                 "platforms;android-8,3\n" +
                                                 "platforms;android-L,4\n" +
                                                 "tools,23.0.2\n";
  public static final String MERGED_FINGERPRINT = "add-ons;addon-google_gdk-google-19,8.0.0\n" +
                                                  "build-tools;18.1.1,18.1.1\n" +
                                                  "build-tools;19.0.0,19.0.0\n" +
                                                  "build-tools;19.0.1,19.0.1\n" +
                                                  "build-tools;19.0.2,19.0.2\n" +
                                                  "build-tools;19.0.3,19.0.3\n" +
                                                  "build-tools;19.1.0,19.1.0\n" +
                                                  "build-tools;20.0.0,20.0.0\n" +
                                                  "docs,1\n" +
                                                  "extras;android;m2repository,6.0.0\n" +
                                                  "extras;android;support,20.0.0\n" +
                                                  "extras;google;m2repository,11.0.0\n" +
                                                  "platform-tools,20.0.0\n" +
                                                  "platforms;android-10,2\n" +
                                                  "platforms;android-14,3\n" +
                                                  "platforms;android-15,3\n" +
                                                  "platforms;android-16,4\n" +
                                                  "platforms;android-17,2\n" +
                                                  "platforms;android-18,2\n" +
                                                  "platforms;android-19,3\n" +
                                                  "platforms;android-20,2\n" +
                                                  "platforms;android-8,3\n" +
                                                  "platforms;android-L,4\n" +
                                                  "tools,23.0.2\n";

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
    StringBuilder s = new StringBuilder();
    RepoManager sdkManager = AndroidSdkHandler.getInstance(sdk).getSdkManager(new StudioLoggerProgressIndicator(getClass()));
    for (LocalPackage p : Sets.newTreeSet(sdkManager.getPackages().getLocalPackages().values())) {
      s.append(p.getPath());
      s.append(',');
      s.append(p.getVersion());
      s.append('\n');
    }
    return s.toString();
  }
}
