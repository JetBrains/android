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
package com.android.tools.res;

import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.resources.ProtoXmlPullParser;
import com.android.ide.common.util.PathString;
import com.android.resources.AarTestUtils;
import com.android.test.testutils.TestUtils;
import com.android.tools.apk.analyzer.ResourceIdResolver;
import com.android.tools.res.apk.ApkTestUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

/**
 * Tests for {@link FileResourceReader}.
 */
public class FileResourceReaderTest {
  @Test
  public void testReadBytes() throws Exception {
    Path resApkPath = TestUtils.resolveWorkspacePath(AarTestUtils.TEST_DATA_DIR + "/design_aar/" + FN_RESOURCE_STATIC_LIBRARY);
    String resourcePath = resApkPath + "!/res/drawable-mdpi-v4/design_ic_visibility.png";
    PathString pathString = new PathString("apk", resourcePath);

    byte[] bytes = FileResourceReader.readBytes(pathString, ResourceIdResolver.NO_RESOLUTION);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);

    bytes = FileResourceReader.readBytes("apk:" + resourcePath);
    assertNotNull(bytes);
    assertEquals(309, bytes.length);
  }

  @Test
  public void testCreateXmlPullParser() throws Exception {
    Path resApkPath = TestUtils.resolveWorkspacePath(AarTestUtils.TEST_DATA_DIR + "/design_aar/" + FN_RESOURCE_STATIC_LIBRARY);
    String resourcePath = resApkPath + "!/res/layout/design_bottom_navigation_item.xml";
    PathString pathString = new PathString("apk", resourcePath);

    XmlPullParser parser = FileResourceReader.createXmlPullParser(pathString, ResourceIdResolver.NO_RESOLUTION);
    assertTrue(parser instanceof ProtoXmlPullParser);
  }

  @Test
  public void testReadBytes_forBinaryEncodedApkEntries() throws Exception {
    Path resApkPath = TestUtils.resolveWorkspacePath(ApkTestUtil.TEST_DATA_DIR + "apk-for-local-test.ap_");
    String resourcePath = resApkPath + "!/res/drawable-anydpi-v24/ic_placeholder_default.xml";
    PathString pathString = new PathString("apk", resourcePath);

    byte[] bytes = FileResourceReader.readBytes(pathString, ResourceIdResolver.NO_RESOLUTION);
    assertNotNull(bytes);

    assertEquals(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<vector\n" +
      "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    xmlns:aapt=\"http://schemas.android.com/aapt\"\n" +
      "    android:height=\"182dp\"\n" +
      "    android:width=\"364dp\"\n" +
      "    android:viewportWidth=\"364\"\n" +
      "    android:viewportHeight=\"182\">\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#FCFCFC\"\n" +
      "        android:pathData=\"M0,0h364v182h-364z\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#7E7576\"\n" +
      "        android:pathData=\"M0,0h364v182h-364z\"\n" +
      "        android:fillAlpha=\"0.02\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#8C4190\"\n" +
      "        android:pathData=\"M0,0h364v182h-364z\"\n" +
      "        android:fillAlpha=\"0.11\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#00000000\"\n" +
      "        android:pathData=\"M171,119L326,119A25,25 0,0 1,351 144L351,144A25,25 0,0 1,326 169L171,169A25,25 0,0 1,146 144L146,144A25,25 0,0 1,171 119z\"\n" +
      "        android:strokeColor=\"#5DD4FB\"\n" +
      "        android:strokeWidth=\"2\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"@ref/0x7f060000\"\n" +
      "        android:pathData=\"M156.023,33.705C154.247,37.971 153.333,42.543 153.333,47.161L188.666,47.161L224,47.161C224,42.543 223.086,37.971 221.31,33.705C219.535,29.44 216.932,25.563 213.651,22.298C210.37,19.033 206.475,16.444 202.188,14.677C197.901,12.91 193.307,12 188.667,12C184.026,12 179.432,12.91 175.145,14.677C170.858,16.444 166.963,19.033 163.682,22.298C160.401,25.563 157.798,29.44 156.023,33.705ZM153.333,47.161C153.333,51.778 152.409,56.35 150.615,60.616C148.821,64.882 146.192,68.758 142.877,72.023C139.562,75.288 135.627,77.878 131.296,79.645C126.965,81.412 122.323,82.322 117.635,82.322C112.947,82.322 108.305,81.412 103.974,79.645C99.643,77.878 95.708,75.288 92.393,72.023C89.078,68.758 86.449,64.882 84.655,60.616C82.861,56.35 81.938,51.778 81.938,47.161L117.635,47.161L153.333,47.161ZM12,47.161C12,42.543 12.904,37.971 14.661,33.705C16.419,29.439 18.994,25.563 22.242,22.298C25.489,19.033 29.344,16.443 33.586,14.676C37.829,12.909 42.376,12 46.968,12C51.561,12 56.108,12.909 60.351,14.676C64.593,16.443 68.448,19.033 71.695,22.298C74.942,25.563 77.518,29.439 79.276,33.705C81.033,37.971 81.938,42.543 81.938,47.161L46.968,47.161L12,47.161Z\"\n" +
      "        android:fillType=\"1\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#00000000\"\n" +
      "        android:pathData=\"M317.068,82.136L317.065,47.103L309.777,81.37L317.057,47.102L302.805,79.105L317.051,47.099L296.456,75.439L317.045,47.094L291.007,70.534L317.04,47.089L286.698,64.602L317.036,47.083L283.716,57.905L317.034,47.076L282.192,50.734L317.033,47.068L282.192,43.403L317.034,47.061L283.716,36.232L317.036,47.054L286.698,29.534L317.04,47.048L291.007,23.603L317.045,47.042L296.456,18.697L317.051,47.038L302.805,15.032L317.057,47.035L309.777,12.766L317.065,47.033L317.068,12L317.072,47.033L324.359,12.766L317.079,47.035L331.332,15.032L317.086,47.038L337.681,18.697L317.092,47.042L343.129,23.603L317.097,47.048L347.438,29.534L317.1,47.054L350.42,36.232L317.102,47.061L351.944,43.403L317.103,47.068L351.944,50.734L317.102,47.076L350.42,57.905L317.1,47.083L347.438,64.602L317.097,47.089L343.129,70.534L317.092,47.094L337.681,75.439L317.086,47.099L331.332,79.105L317.079,47.102L324.359,81.37L317.072,47.103L317.068,82.136Z\"\n" +
      "        android:strokeColor=\"#FF8B5E\"\n" +
      "        android:strokeWidth=\"1\"\n" +
      "        android:strokeLineJoin=\"1\" />\n" +
      "\n" +
      "    <path\n" +
      "        android:fillColor=\"#FFA8FF\"\n" +
      "        android:pathData=\"M38.205,170L38.975,170A26.205,26.205 89.056,0 0,65.18 143.795L65.18,143.795A26.205,26.205 89.056,0 0,38.975 117.59L38.205,117.59A26.205,26.205 89.056,0 0,12 143.795L12,143.795A26.205,26.205 89.056,0 0,38.205 170z\" />\n" +
      "</vector>\n",
      new String(bytes, StandardCharsets.US_ASCII)
    );
  }
}
