/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import java.util.Collection;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

/**
 * Tests for {@link NonGradleApkProvider}.
 */
public class NonGradleApkProviderTest extends AndroidTestCase {

  public void testGetApks() throws Exception {
    IDevice device = Mockito.mock(IDevice.class);

    myFacet.getProperties().APK_PATH = "artifact.apk";

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, new NonGradleApplicationIdProvider(myFacet), null);

    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("p1.p2", apk.getApplicationId());
    assertTrue(apk.getFiles().get(0).getApkFile().getPath().endsWith("artifact.apk"));
  }
}
