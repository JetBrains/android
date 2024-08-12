/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.aspect;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Files;
import com.google.idea.blaze.aspect.CreateAar.AarOptions;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link CreateAar}. */
@RunWith(JUnit4.class)
public class CreateAarTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void fullIntegrationTest() throws IOException {
    File outputAar = new File(folder.getRoot(), "generated.aar");
    File manifest = folder.newFile("AndroidManifest.xml");
    String manifestContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
            + "          package=\"com.google.android.assets.quantum\">"
            + "    <uses-sdk android:minSdkVersion=\"4\"/>"
            + "    <application/>"
            + "</manifest>";
    Files.asCharSink(manifest, UTF_8).write(manifestContent);
    File values = folder.newFolder("res", "values");
    File colors = new File(values, "colors.xml");
    String colorsContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<resources>"
            + "    <color name=\"quantum_black_100\">#000000</color>"
            + "</resources>";
    Files.asCharSink(colors, UTF_8).write(colorsContent);

    String[] args =
        new String[] {
          "--aar",
          outputAar.getPath(),
          "--manifest_file",
          manifest.getPath(),
          "--resources",
          colors.getPath(),
          "--resource_root",
          values.getParent()
        };
    AarOptions options = CreateAar.parseArgs(args);
    CreateAar.main(options);
    assertThat(outputAar.exists()).isTrue();
    ZipFile aar = new ZipFile(outputAar);
    assertThat(getContent(aar.getInputStream(aar.getEntry("AndroidManifest.xml"))))
        .isEqualTo(manifestContent);
    assertThat(getContent(aar.getInputStream(aar.getEntry("res/values/colors.xml"))))
        .isEqualTo(colorsContent);
    assertThat(aar.size()).isEqualTo(2);
  }

  /** Tests the setup where resource are present in a directory that is not called `res` */
  @Test
  public void fullIntegrationTest_nonResDirectory_outputsValidAAR() throws IOException {
    File outputAar = new File(folder.getRoot(), "generated.aar");
    File manifest = folder.newFile("AndroidManifest.xml");
    String manifestContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
            + "          package=\"com.google.android.assets.quantum\">"
            + "    <uses-sdk android:minSdkVersion=\"4\"/>"
            + "    <application/>"
            + "</manifest>";
    Files.asCharSink(manifest, UTF_8).write(manifestContent);
    File values = folder.newFolder("something_else", "values");
    File colors = new File(values, "colors.xml");
    String colorsContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<resources>"
            + "    <color name=\"quantum_black_100\">#000000</color>"
            + "</resources>";
    Files.asCharSink(colors, UTF_8).write(colorsContent);

    String[] args =
        new String[] {
          "--aar",
          outputAar.getPath(),
          "--manifest_file",
          manifest.getPath(),
          "--resources",
          colors.getPath(),
          "--resource_root",
          values.getParent()
        };
    AarOptions options = CreateAar.parseArgs(args);
    CreateAar.main(options);
    assertThat(outputAar.exists()).isTrue();
    ZipFile aar = new ZipFile(outputAar);
    assertThat(getContent(aar.getInputStream(aar.getEntry("AndroidManifest.xml"))))
        .isEqualTo(manifestContent);
    assertThat(getContent(aar.getInputStream(aar.getEntry("res/values/colors.xml"))))
        .isEqualTo(colorsContent);
    assertThat(aar.size()).isEqualTo(2);
  }

  /** Tests the setup where the relevant manifest is not called AndroidManifest.xml */
  @Test
  public void fullIntegrationTest_funkyManifestName_outputsValidAAR() throws IOException {
    File outputAar = new File(folder.getRoot(), "generated.aar");
    File manifest = folder.newFile("my_funky_android_manifest.xml");
    String manifestContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
            + "          package=\"com.google.android.assets.quantum\">"
            + "    <uses-sdk android:minSdkVersion=\"4\"/>"
            + "    <application/>"
            + "</manifest>";
    Files.asCharSink(manifest, UTF_8).write(manifestContent);
    File values = folder.newFolder("res", "values");
    File colors = new File(values, "colors.xml");
    String colorsContent =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<resources>"
            + "    <color name=\"quantum_black_100\">#000000</color>"
            + "</resources>";
    Files.asCharSink(colors, UTF_8).write(colorsContent);

    String[] args =
        new String[] {
          "--aar",
          outputAar.getPath(),
          "--manifest_file",
          manifest.getPath(),
          "--resources",
          colors.getPath(),
          "--resource_root",
          values.getParent()
        };
    AarOptions options = CreateAar.parseArgs(args);
    CreateAar.main(options);
    assertThat(outputAar.exists()).isTrue();
    ZipFile aar = new ZipFile(outputAar);
    assertThat(getContent(aar.getInputStream(aar.getEntry("AndroidManifest.xml"))))
        .isEqualTo(manifestContent);
    assertThat(getContent(aar.getInputStream(aar.getEntry("res/values/colors.xml"))))
        .isEqualTo(colorsContent);
    assertThat(aar.size()).isEqualTo(2);
  }

  private static String getContent(InputStream in) throws IOException {
    StringBuilder out = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        out.append(line);
      }
    }
    return out.toString();
  }
}
