/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ProtoXmlPrettyPrinterTest {

  @Test
  public void decodeInvalidFileThrows() {
    // Prepare
    byte[] contents = new byte[] {0x03, 0x00};
    ProtoXmlPrettyPrinter prettyPrinter = new ProtoXmlPrettyPrinterImpl();

    // Act
    try {
      @SuppressWarnings("unused")
      String ignored = prettyPrinter.prettyPrint(contents);
      fail("prettyPrint should fail to decode invalid Proto XML content");
    } catch(IOException expected) {
    }
  }

  @Test
  public void decodeValidFileWorks() throws IOException {
    // Prepare
    final String manifestXmlPath = "base/manifest/" + SdkConstants.FN_ANDROID_MANIFEST_XML;
    byte[] contents = readAppBundleFileEntry(manifestXmlPath);
    ProtoXmlPrettyPrinterImpl prettyPrinter = new ProtoXmlPrettyPrinterImpl();

    // Act
    String xml = prettyPrinter.prettyPrint(contents);

    // Assert
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    package=\"com.example.myapplication\"\n" +
                 "    android:versionCode=\"1\"\n" +
                 "    android:versionName=\"1.0\" >\n" +
                 "\n" +
                 "    <uses-sdk\n" +
                 "        android:minSdkVersion=\"21\"\n" +
                 "        android:targetSdkVersion=\"27\" />\n" +
                 "\n" +
                 "    <application\n" +
                 "        android:allowBackup=\"true\"\n" +
                 "        android:debuggable=\"true\"\n" +
                 "        android:icon=\"@mipmap/ic_launcher\"\n" +
                 "        android:label=\"@string/app_name\"\n" +
                 "        android:roundIcon=\"@mipmap/ic_launcher_round\"\n" +
                 "        android:supportsRtl=\"true\"\n" +
                 "        android:theme=\"@style/AppTheme\" >\n" +
                 "        <activity\n" +
                 "            android:name=\"com.example.myapplication.MainActivity\"\n" +
                 "            android:label=\"@string/app_name\"\n" +
                 "            android:theme=\"@style/AppTheme.NoActionBar\" >\n" +
                 "            <intent-filter>\n" +
                 "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                 "\n" +
                 "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                 "            </intent-filter>\n" +
                 "        </activity>\n" +
                 "    </application>\n" +
                 "\n" +
                 "</manifest>\n", xml.replace("\r\n", "\n"));
  }

  @NotNull
  private static byte[] readAppBundleFileEntry(@SuppressWarnings("SameParameterValue") @NotNull String path) throws IOException {
    byte[] contents = null;
    try (InputStream file = ProtoXmlPrettyPrinterTest.class.getResourceAsStream("/bundle.aab")) {
      try (ZipInputStream zip = new ZipInputStream(file)) {
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
          if (entry.getName().equals(path)) {
            contents = IOUtils.toByteArray(zip);
            break;
          }
          zip.closeEntry();
        }
      }
    }
    if (contents == null) {
      throw new IOException(String.format("Invalid app bundle file, entry \"%s\" not found", path));
    }
    return contents;
  }
}
