/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.assets;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.w3c.dom.Document;

public class VectorAssetTest {

  @Test
  public void parseXml_disallowsDtd() {
    String xxeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"http://google.com\"> ]>\n" +
                    "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:width=\"24dp\"\n" +
                    "    android:height=\"24dp\"\n" +
                    "    android:viewportWidth=\"24.0\"\n" +
                    "    android:viewportHeight=\"24.0\">\n" +
                    "  <path\n" +
                    "      android:fillColor=\"#FF000000\"\n" +
                    "      android:pathData=\"M20,11H7.83l5.59-5.59L12,4l-8,8l8,8l1.41-1.41L7.83,13H20V11z\"/>\n" +
                    "</vector>";

    StringBuilder errorLog = new StringBuilder();
    Document doc = VectorAsset.parseXml(xxeXml, errorLog);

    // DocumentBuilder.parse should throw an exception or return null because DTD is disallowed.
    assertThat(doc).isNull();
    assertThat(errorLog.toString()).contains("Exception while parsing XML file");
  }

  @Test
  public void parseXml_allowsLegitimateXml() {
    String legitimateXml = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    android:width=\"24dp\"\n" +
                           "    android:height=\"24dp\"\n" +
                           "    android:viewportWidth=\"24.0\"\n" +
                           "    android:viewportHeight=\"24.0\">\n" +
                           "  <path\n" +
                           "      android:fillColor=\"#FF000000\"\n" +
                           "      android:pathData=\"M20,11H7.83l5.59-5.59L12,4l-8,8l8,8l1.41-1.41L7.83,13H20V11z\"/>\n" +
                           "</vector>";

    StringBuilder errorLog = new StringBuilder();
    Document doc = VectorAsset.parseXml(legitimateXml, errorLog);

    assertThat(doc).isNotNull();
    assertThat(errorLog.toString()).isEmpty();
  }
}
