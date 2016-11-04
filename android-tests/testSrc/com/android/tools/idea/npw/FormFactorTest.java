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
package com.android.tools.idea.npw;

import com.android.sdklib.repository.targets.SystemImage;
import org.junit.Test;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FormFactorTest {
  private final static int KITKAT_WATCH = 20;
  private final static int LOLLIPOP = 21;

  @Test
  public void mobileSupportedOnLollipopApi() {
    assertTrue(MOBILE.isSupported(SystemImage.DEFAULT_TAG, LOLLIPOP));
  }

  @Test
  public void mobileNotSupportedOnWhiteList() {
    assertFalse(MOBILE.isSupported(SystemImage.WEAR_TAG, LOLLIPOP));
    assertFalse(MOBILE.isSupported(null, LOLLIPOP));
  }

  @Test
  public void mobileNotSupportedOnWatchApi() {
    // Tests that mobile is on the black-list for the watch API
    assertFalse(MOBILE.isSupported(SystemImage.DEFAULT_TAG, KITKAT_WATCH));
  }
}
