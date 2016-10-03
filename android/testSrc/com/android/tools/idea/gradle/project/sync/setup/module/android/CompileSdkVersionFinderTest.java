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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.tools.idea.gradle.project.sync.setup.module.android.JdkModuleSetupStep.CompileSdkVersionFinder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link CompileSdkVersionFinder}.
 */
public class CompileSdkVersionFinderTest {
  private CompileSdkVersionFinder myFinder;

  @Before
  public void setUp() {
    myFinder = new CompileSdkVersionFinder();
  }

  @Test
  public void findOffsetIn() {
    String contents = "apply plugin: 'com.android.application'\n" +
                      "\n" +
                      "android {\n" +
                      "    compileSdkVersion 21\n" +
                      "    buildToolsVersion \"21.1.1\"\n" +
                      "\n" +
                      "    defaultConfig {\n" +
                      "        applicationId \"com.android.test.myapplication\"\n" +
                      "        minSdkVersion 21\n" +
                      "        targetSdkVersion 21\n" +
                      "        versionCode 1\n" +
                      "        versionName \"1.0\"\n" +
                      "    }\n" +
                      "    buildTypes {\n" +
                      "        release {\n" +
                      "            minifyEnabled false\n" +
                      "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'\n" +
                      "        }\n" +
                      "    }\n" +
                      "}\n" +
                      "\n" +
                      "dependencies {\n" +
                      "    compile fileTree(dir: 'libs', include: ['*.jar'])\n" +
                      "}";
    int offset = myFinder.findOffsetIn(contents);
    assertEquals(73, offset);
  }
}