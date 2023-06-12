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

import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.getTextForFile;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.testFramework.RunsInEdt;
import org.junit.Rule;
import org.junit.Test;

/**
 * This tests unused resource removal for a Gradle project. The JPS scenario is
 * tested in {@link UnusedResourcesTest}.
 */
@RunsInEdt
public class UnusedResourcesGradleTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Test
  public void testGroovy() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_GROOVY);
    openPreparedTestProject(preparedProject, project -> {
      assertTrue(getTextForFile(project, "app/build.gradle").contains("resValue"));
      UnusedResourcesHandler.invoke(project, null, null, true, true);

      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"app_name\">Hello World</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/src/main/res/values/strings.xml"));

      assertFalse(getTextForFile(project, "app/build.gradle").contains("resValue"));
    });
  }

  @Test
  public void testGroovyMultiModule() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {
      assertTrue(getTextForFile(project, "app/build.gradle").contains("resValue"));

      UnusedResourcesHandler.invoke(project, null, null, true, true);

      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"app_name\">Hello World</string>\n" +
                   "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/src/main/res/values/strings.xml"));

      assertFalse(getTextForFile(project, "app/build.gradle").contains("resValue"));
    });
  }

  @Test
  public void testSpecificModule() {
    // Run find usages on the app module, and make sure that we limit the removal just to the
    // app module; we leave unused resources in other modules alone (such as the unused resource
    // in the library)
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module app = moduleManager.findModuleByName("project.app.main"); // module name derived from test name + gradle name
      assertNotNull(app);

      UnusedResourcesHandler.invoke(project, new Module[]{app}, null, true, true);

      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"app_name\">Hello World</string>\n" +
                   "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/src/main/res/values/strings.xml"));

      // Make sure it *didn't* delete resources from the library since it's not included in the module list!
      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"unusedlib\">Unused string in library</string>\n" +
                   "    <string name=\"usedlib\">String used from app</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml"));
    });
  }

  @Test
  public void testUsedDownstream() {
    // Run find usages on a library, and make sure that (a) only unused resources in the library are removed, and
    // (b) that we take into account downstream usages (e.g. in app) and don't consider those unused in the analysis
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      Module app =
        moduleManager.findModuleByName("project.app.mylibrary.main"); // module name derived from test name + gradle name
      assertNotNull(app);

      UnusedResourcesHandler.invoke(project, new Module[]{app}, null, true, true);

      // Make sure we have NOT deleted the unused resources in app
      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"app_name\">Hello World</string>\n" +
                   "    <string name=\"newstring\">@string/usedlib</string>\n" +
                   "    <string name=\"used_from_test\">Referenced from test</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/src/main/res/values/strings.xml"));

      // Make sure we have removed the unused resource in the library (@string/unusedlib), but we
      // have *not* removed the resource which is unused in the library but still referenced outside of
      // it (in app)
      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"usedlib\">String used from app</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml"));
    });
  }

  @Test
  public void testKotlin() {
    // Like testGroovy, but this one verifies analysis and updating of build.gradle.kts files instead.
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_KTS);
    openPreparedTestProject(preparedProject, project -> {
      assertTrue(getTextForFile(project, "app/build.gradle.kts").contains("resValue"));

      UnusedResourcesHandler.invoke(project, null, null, true, true);

      assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<resources>\n" +
                   "    <string name=\"app_name\">Hello World</string>\n" +
                   "</resources>\n",
                   getTextForFile(project, "app/src/main/res/values/strings.xml"));

      assertFalse(getTextForFile(project, "app/build.gradle.kts").contains("resValue"));
    });
  }
}