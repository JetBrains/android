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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;

import java.io.IOException;

/**
 * Tests for {@link ExtModel}.
 */
public class ExtModelTest extends GradleFileModelTestCase {

  public void testParsingSimplePropertyPerLine() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "ext.srcDirName = 'src/java'";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    assertNotNull(extModel);

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    String srcDirName = extModel.getProperty("srcDirName", String.class);
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName);
  }

  public void testParsingSimplePropertyInExtBlock() throws IOException {
    String text = "ext {\n" +
                  "   COMPILE_SDK_VERSION = 21\n" +
                  "   srcDirName = 'src/java'\n" +
                  "}";

    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    assertNotNull(extModel);

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    String srcDirName = extModel.getProperty("srcDirName", String.class);
    assertNotNull(srcDirName);
    assertEquals("src/java", srcDirName);
  }

  public void testParsingListOfProperties() throws IOException {
    String text = "ext.libraries = [\n" +
                  "    guava: \"com.google.guava:guava:19.0-rc1\",\n" +
                  "    design :  \"com.android.support:design:22.2.1\"\n" +
                  "]";
    writeToBuildFile(text);

    ExtModel extModel = getGradleBuildModel().ext();
    assertNotNull(extModel);

    String guavaLibrary = extModel.getProperty("libraries.guava", String.class);
    assertNotNull(guavaLibrary);
    assertEquals("com.google.guava:guava:19.0-rc1", guavaLibrary);
  }

  public void testResolveExtProperty() throws IOException {
    String text = "ext.COMPILE_SDK_VERSION = 21\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel);

    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());
  }

  public void testResolveQualifiedExtProperty() throws IOException {
    String text = "ext.constants = [\n" +
                  "  COMPILE_SDK_VERSION : 21\n" +
                  "]\n" +
                  "android {\n" +
                  "  compileSdkVersion constants.COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel);

    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    Integer compileSdkVersion = extModel.getProperty("constants.COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());
  }

  public void testResolveMultiLevelExtProperty() throws IOException {
    String text = "ext.SDK_VERSION = 21\n" +
                  "ext.COMPILE_SDK_VERSION = SDK_VERSION\n" +
                  "android {\n" +
                  "  compileSdkVersion COMPILE_SDK_VERSION\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    assertNotNull(buildModel);

    ExtModel extModel = buildModel.ext();
    assertNotNull(extModel);

    Integer sdkVersion = extModel.getProperty("SDK_VERSION", Integer.class);
    assertNotNull(sdkVersion);
    assertEquals(21, sdkVersion.intValue());

    Integer compileSdkVersion = extModel.getProperty("COMPILE_SDK_VERSION", Integer.class);
    assertNotNull(compileSdkVersion);
    assertEquals(21, compileSdkVersion.intValue());

    AndroidModel androidModel = buildModel.android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "21", androidModel.compileSdkVersion());
  }
}