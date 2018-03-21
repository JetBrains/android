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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApkFileSystemTest {
  @Test
  public void binaryXmlPaths() {
    byte[] contents = new byte[] {0x03, 0x00};
    assertTrue(ApkFileSystem.isBinaryXml(SdkConstants.FN_ANDROID_MANIFEST_XML, contents));
    assertTrue(ApkFileSystem.isBinaryXml("res/layout/foo.xml", contents));

    assertTrue(ApkFileSystem.isBinaryXml("res/values/dimens.xml", contents));
    assertFalse(ApkFileSystem.isBinaryXml("res/values/dimens.txt", contents));

    assertFalse(ApkFileSystem.isBinaryXml("res/raw/foo.xml", contents));
    assertFalse(ApkFileSystem.isBinaryXml("res/raw-land/foo.xml", contents));

    assertFalse(ApkFileSystem.isBinaryXml("res/values/dimens.xml", new byte[] {0x3f, 0x3c}));
  }
}
