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
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.RunsInEdt;
import java.nio.file.Paths;
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
      assertThat(getTextForFile(project, "mipmap/build.gradle")).contains("resValue");

      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, null);
      processor.setIncludeIds(true);
      processor.run();

      assertThat(getTextForFile(project, "mipmap/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "</resources>\n");

      String appBuildGradleText = getTextForFile(project, "mipmap/build.gradle");
      assertThat(appBuildGradleText).doesNotContain("resValue");
      assertThat(appBuildGradleText).contains("android");
    });
  }

  @Test
  public void testGroovyMultiModule() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {
      assertThat(getTextForFile(project, "app/build.gradle")).contains("resValue");

      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, null);
      processor.setIncludeIds(true);
      processor.run();

      assertThat(getTextForFile(project, "app/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "    <string name=\"used_from_test\">Referenced from test</string>\n" +
        "</resources>\n");

      String appBuildGradleText = getTextForFile(project, "app/build.gradle");
      assertThat(appBuildGradleText).doesNotContain("resValue");
      assertThat(appBuildGradleText).contains("android");
    });
  }

  @Test
  public void testSpecificModule() {
    // Run find usages on the app module, and make sure that we limit the removal just to the
    // app module; we leave unused resources in other modules alone (such as the unused resource
    // in the library)
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      VirtualFile moduleDir = VfsUtil.findFile(Paths.get(project.getBasePath(), "app/src/main"), false);
      PsiDirectory modulePsiDir = PsiManager.getInstance(project).findDirectory(moduleDir);

      UnusedResourcesProcessor.FileFilter filter = UnusedResourcesProcessor.FileFilter.from(ImmutableSet.of(modulePsiDir));
      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, filter);
      processor.setIncludeIds(true);
      processor.run();

      assertThat(getTextForFile(project, "app/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "    <string name=\"used_from_test\">Referenced from test</string>\n" +
        "</resources>\n");

      // Make sure it *didn't* delete resources from the library since it's not included in the module list!
      assertThat(getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"unusedlib\">Unused string in library</string>\n" +
        "    <string name=\"usedlib\">String used from app</string>\n" +
        "</resources>\n");
    });
  }

  @Test
  public void testUsedDownstream() {
    // Run find usages on a library, and make sure that (a) only unused resources in the library are removed, and
    // (b) that we take into account downstream usages (e.g. in app) and don't consider those unused in the analysis
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      VirtualFile moduleDir = VfsUtil.findFile(Paths.get(project.getBasePath(), "app/mylibrary/src/main"), false);
      PsiDirectory modulePsiDir = PsiManager.getInstance(project).findDirectory(moduleDir);

      UnusedResourcesProcessor.FileFilter filter = UnusedResourcesProcessor.FileFilter.from(ImmutableSet.of(modulePsiDir));
      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, filter);
      processor.setIncludeIds(true);
      processor.run();

      // Make sure we have NOT deleted the unused resources in app
      assertThat(getTextForFile(project, "app/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "    <string name=\"newstring\">@string/usedlib</string>\n" +
        "    <string name=\"used_from_test\">Referenced from test</string>\n" +
        "</resources>\n");

      // Make sure we have removed the unused resource in the library (@string/unusedlib), but we
      // have *not* removed the resource which is unused in the library but still referenced outside of
      // it (in app)
      assertThat(getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"usedlib\">String used from app</string>\n" +
        "</resources>\n");
    });
  }

  @Test
  public void testKotlin() {
    // Like testGroovy, but this one verifies analysis and updating of build.gradle.kts files instead.
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_KTS);
    openPreparedTestProject(preparedProject, project -> {
      assertThat(getTextForFile(project, "mipmap/build.gradle.kts")).contains("resValue");

      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, null);
      processor.setIncludeIds(true);
      processor.run();

      assertThat(getTextForFile(project, "mipmap/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "</resources>\n");

      String appBuildGradleKtsText = getTextForFile(project, "mipmap/build.gradle.kts");
      assertThat(appBuildGradleKtsText).doesNotContain("resValue");
      assertThat(appBuildGradleKtsText).contains("android");
    });
  }

  @Test
  public void testSingleFile() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      VirtualFile valuesFile = VfsUtil.findFile(Paths.get(project.getBasePath(), "app/mylibrary/src/main/res/values/values.xml"), false);
      PsiFile valuesPsiFile = PsiManager.getInstance(project).findFile(valuesFile);

      UnusedResourcesProcessor.FileFilter filter = UnusedResourcesProcessor.FileFilter.from(ImmutableSet.of(valuesPsiFile));
      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, filter);
      processor.setIncludeIds(true);
      processor.run();

      // Make sure that the main module's file was *not* modified.
      assertThat(getTextForFile(project, "app/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "    <string name=\"newstring\">@string/usedlib</string>\n" +
        "    <string name=\"used_from_test\">Referenced from test</string>\n" +
        "</resources>\n");

      // Unused string should be deleted from the library file.
      assertThat(getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"usedlib\">String used from app</string>\n" +
        "</resources>\n");
    });
  }

  @Test
  public void testMultipleFiles() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.UNUSED_RESOURCES_MULTI_MODULE);
    openPreparedTestProject(preparedProject, project -> {

      VirtualFile stringsFile = VfsUtil.findFile(Paths.get(project.getBasePath(), "app/src/main/res/values/strings.xml"), false);
      PsiFile stringsPsiFile = PsiManager.getInstance(project).findFile(stringsFile);

      VirtualFile valuesFile = VfsUtil.findFile(Paths.get(project.getBasePath(), "app/mylibrary/src/main/res/values/values.xml"), false);
      PsiFile valuesPsiFile = PsiManager.getInstance(project).findFile(valuesFile);

      UnusedResourcesProcessor.FileFilter filter = UnusedResourcesProcessor.FileFilter.from(ImmutableSet.of(stringsPsiFile, valuesPsiFile));
      UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, filter);
      processor.setIncludeIds(true);
      processor.run();

      assertThat(getTextForFile(project, "app/src/main/res/values/strings.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"app_name\">Hello World</string>\n" +
        "    <string name=\"used_from_test\">Referenced from test</string>\n" +
        "</resources>\n");

      assertThat(getTextForFile(project, "app/mylibrary/src/main/res/values/values.xml")).isEqualTo(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<resources>\n" +
        "    <string name=\"usedlib\">String used from app</string>\n" +
        "</resources>\n");
    });
  }
}