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
package org.jetbrains.android.refactoring;

import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;

import static com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.testing.TestProjectPaths.UNUSED_RESOURCES;

/**
 * This tests unused resource removal for a Gradle project. The JPS scenario is
 * tested in {@link UnusedResourcesTest}.
 */
public class UnusedResourcesGradleTest extends AndroidGradleTestCase {

  public void test() throws Exception {
    loadProject(UNUSED_RESOURCES);

    UnusedResourcesHandler.invoke(getProject(), null, null, true, true);

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">Hello World</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/strings.xml"));

    assertEquals("apply plugin: 'com.android.application'\n" +
                 "\n" +
                 "android {\n" +
                 "  compileSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "  buildToolsVersion '" + CURRENT_BUILD_TOOLS_VERSION + "'\n" +
                 "\n" +
                 "  defaultConfig {\n" +
                 "    minSdkVersion 15\n" +
                 "    targetSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    applicationId 'com.example.android.app'\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "repositories {\n" + AndroidGradleTests.getLocalRepositories() +
                 "}\n",
                 getTextForFile("app/build.gradle"));
  }
}