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

package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Abi;

import junit.framework.TestCase;

import static com.android.sdklib.repository.targets.SystemImage.*;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationFromParts;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.*;

public class ChooseSystemImagePanelTest extends TestCase {

  public void testImageRecommendations() throws Exception {
    assertEquals(X86, getClassificationFromParts(Abi.X86, 21, GOOGLE_APIS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 22, GOOGLE_APIS_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 23, DEFAULT_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 24, GOOGLE_APIS_X86_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86_64, 25, GOOGLE_APIS_X86_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, GOOGLE_APIS_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, GOOGLE_APIS_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, GOOGLE_APIS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, WEAR_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, WEAR_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 25, GLASS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, TV_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, TV_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 25, DEFAULT_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, DEFAULT_TAG));
  }
}
