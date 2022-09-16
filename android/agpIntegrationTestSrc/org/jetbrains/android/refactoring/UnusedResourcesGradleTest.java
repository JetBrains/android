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

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.adtimport.GradleImport;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;

import static com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION;
import static com.android.tools.idea.testing.TestProjectPaths.UNUSED_RESOURCES_GROOVY;
import static com.android.tools.idea.testing.TestProjectPaths.UNUSED_RESOURCES_KTS;
import static com.android.tools.idea.testing.TestProjectPaths.UNUSED_RESOURCES_MULTI_MODULE;
import static java.util.Collections.emptyList;

/**
 * This tests unused resource removal for a Gradle project. The JPS scenario is
 * tested in {@link UnusedResourcesTest}.
 */
public class UnusedResourcesGradleTest extends AndroidGradleTestCase {

  public void testGroovy() throws Exception {
    loadProject(UNUSED_RESOURCES_GROOVY);

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
                 "    minSdkVersion " + SdkVersionInfo.LOWEST_ACTIVE_API + "\n" +
                 "    targetSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    applicationId 'com.example.android.app'\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "repositories {\n" + AndroidGradleTests.getLocalRepositoriesForGroovy(emptyList()) +
                 "}\n",
                 getTextForFile("app/build.gradle"));
  }

  public void testGroovyMultiModule() throws Exception {
    loadProject(UNUSED_RESOURCES_MULTI_MODULE);

    UnusedResourcesHandler.invoke(getProject(), null, null, true, true);

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">Hello World</string>\n" +
                 "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/strings.xml"));

    assertEquals("apply plugin: 'com.android.application'\n" +
                 "\n" +
                 "android {\n" +
                 "  compileSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "  buildToolsVersion '" + CURRENT_BUILD_TOOLS_VERSION + "'\n" +
                 "\n" +
                 "  defaultConfig {\n" +
                 "    minSdkVersion " + SdkVersionInfo.LOWEST_ACTIVE_API + "\n" +
                 "    targetSdkVersion " + GradleImport.CURRENT_COMPILE_VERSION + "\n" +
                 "    applicationId 'com.example.android.app'\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "dependencies {\n" +
                 "    implementation project(path: ':app:mylibrary')\n" +
                 "    testImplementation 'junit:junit:4.12'\n" +
                 "}\n" +
                 "\n" +
                 "repositories {\n" + AndroidGradleTests.getLocalRepositoriesForGroovy(emptyList()) +
                 "}\n",
                 getTextForFile("app/build.gradle"));
  }

  public void testSpecificModule() throws Exception {
    // Run find usages on the app module, and make sure that we limit the removal just to the
    // app module; we leave unused resources in other modules alone (such as the unused resource
    // in the library)
    loadProject(UNUSED_RESOURCES_MULTI_MODULE);

    Project project = getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module app = moduleManager.findModuleByName("testSpecificModule.app.main"); // module name derived from test name + gradle name
    assertNotNull(app);

    UnusedResourcesHandler.invoke(getProject(), new Module[] { app }, null, true, true);

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">Hello World</string>\n" +
                 "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/strings.xml"));

    // Make sure it *didn't* delete resources from the library since it's not included in the module list!
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"unusedlib\">Unused string in library</string>\n" +
                 "    <string name=\"usedlib\">String used from app</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/mylibrary/src/main/res/values/values.xml"));
  }

  public void testUsedDownstream() throws Exception {
    // Run find usages on a library, and make sure that (a) only unused resources in the library are removed, and
    // (b) that we take into account downstream usages (e.g. in app) and don't consider those unused in the analysis
    loadProject(UNUSED_RESOURCES_MULTI_MODULE);

    Project project = getProject();
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module app = moduleManager.findModuleByName("testUsedDownstream.app.mylibrary.main"); // module name derived from test name + gradle name
    assertNotNull(app);

    UnusedResourcesHandler.invoke(getProject(), new Module[] { app }, null, true, true);

    // Make sure we have NOT deleted the unused resources in app
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">Hello World</string>\n" +
                 "    <string name=\"newstring\">@string/usedlib</string>\n" +
                 "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/strings.xml"));

    // Make sure we have removed the unused resource in the library (@string/unusedlib), but we
    // have *not* removed the resource which is unused in the library but still referenced outside of
    // it (in app)
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"usedlib\">String used from app</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/mylibrary/src/main/res/values/values.xml"));
  }

  public void testKotlin() throws Exception {
    // Like testGroovy, but this one verifies analysis and updating of build.gradle.kts files instead.
    loadProject(UNUSED_RESOURCES_KTS);

    UnusedResourcesHandler.invoke(getProject(), null, null, true, true);

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<resources>\n" +
                 "    <string name=\"app_name\">Hello World</string>\n" +
                 "</resources>\n",
                 getTextForFile("app/src/main/res/values/strings.xml"));

    assertEquals("plugins {\n" +
                 "  id(\"com.android.application\")\n" +
                 "  kotlin(\"android\")\n" +
                 "  kotlin(\"android.extensions\")\n" +
                 "}\n" +
                 "apply(plugin = \"com.android.application\")\n" +
                 "\n" +
                 "android {\n" +
                 "  compileSdkVersion(" + GradleImport.CURRENT_COMPILE_VERSION + ")\n" +
                 "  buildToolsVersion(\"" + CURRENT_BUILD_TOOLS_VERSION + "\")\n" +
                 "\n" +
                 "  defaultConfig {\n" +
                 "    targetSdkVersion(" + GradleImport.CURRENT_COMPILE_VERSION + ")\n" +
                 "    applicationId = \"com.example.android.app\"\n" +
                 "  }\n" +
                 "}\n" +
                 "\n" +
                 "repositories {\n" + AndroidGradleTests.getLocalRepositoriesForKotlin(emptyList()) +
                 "}\n",
                 getTextForFile("app/build.gradle.kts"));
  }
}