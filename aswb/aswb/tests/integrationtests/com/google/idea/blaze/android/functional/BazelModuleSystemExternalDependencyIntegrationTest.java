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
package com.google.idea.blaze.android.functional;

import static com.android.ide.common.repository.GoogleMavenArtifactIdCompat.CONSTRAINT_LAYOUT_COORDINATE;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.libraries.UnpackedAarUtils;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.projectsystem.BazelModuleSystem;
import com.google.idea.blaze.android.projectsystem.DesugaringLibraryConfigFilesLocator;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration test for external dependency management methods in {@link
 * BazelModuleSystem}.
 */
@RunWith(JUnit4.class)
public class BazelModuleSystemExternalDependencyIntegrationTest
    extends BlazeAndroidIntegrationTestCase {
  private static final String CONSTRAINT_LAYOUT_LABEL =
      "//third_party/java/android/android_sdk_linux/extras/android/compatibility/constraint_layout:constraint_layout";

  @Before
  public void setupSourcesAndProjectView() {
    registerExtension(
        DesugaringLibraryConfigFilesLocator.EP_NAME,
        new DesugaringLibraryConfigFilesLocator() {
          @Override
          public boolean getDesugarLibraryConfigFilesKnown() {
            return true;
          }

          @Override
          public ImmutableList<Path> getDesugarLibraryConfigFiles(Project project) {
            return ImmutableList.of(Paths.get("a/a.json"), Paths.get("b/b.json"));
          }

          @Override
          public BuildSystemName buildSystem() {
            return BuildSystemName.Blaze;
          }
        });
    registerExtension(
        MavenArtifactLocator.EP_NAME,
        new MavenArtifactLocator() {
          private final ImmutableMap<GradleCoordinate, Label> knownArtifacts =
              new ImmutableMap.Builder<GradleCoordinate, Label>()
                  .put(CONSTRAINT_LAYOUT_COORDINATE, Label.create(CONSTRAINT_LAYOUT_LABEL))
                  .build();

          @Override
          public Label labelFor(GradleCoordinate coordinate) {
            return knownArtifacts.get(
                new GradleCoordinate(coordinate.getGroupId(), coordinate.getArtifactId(), "+"));
          }

          @Override
          public BuildSystemName buildSystem() {
            return BuildSystemName.Bazel;
          }
        });

    setProjectView(
        "directories:",
        "  java/com/foo/gallery/activities",
        "targets:",
        "  //java/com/foo/gallery/activities:activities",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
        "package com.foo.gallery.activities",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/libs/res/values/styles.xml"),
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<resources></resources>");
  }

  @Test
  public void getResolvedDependency_missingDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res"));
    runFullBlazeSyncWithNoIssues();

    Module activityModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BazelModuleSystem workspaceModuleSystem = BazelModuleSystem.getInstance(activityModule);
    assertThat(workspaceModuleSystem.getResolvedDependency(CONSTRAINT_LAYOUT_COORDINATE)).isNull();
  }

  @Test
  public void getResolvedDependency_transitiveDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res").dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BazelModuleSystem workspaceModuleSystem = BazelModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getResolvedDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNotNull();
  }

  @Test
  public void getRegisteredDependency_nullForMissingDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res("res"));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BazelModuleSystem workspaceModuleSystem = BazelModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNull();
  }

  @Test
  public void getRegisteredDependency_nullForTransitiveDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res").dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BazelModuleSystem workspaceModuleSystem = BazelModuleSystem.getInstance(workspaceModule);

    // getRegisteredDependency should return null for a dependency as long as it's not declared by
    // the module itself.
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNull();
  }

  @Test
  public void getRegisteredDependency_findsFirstLevelDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res("res")
            .dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BazelModuleSystem workspaceModuleSystem = BazelModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNotNull();
  }

  private File getAarDir(File aarLibraryFile) {
    String path = aarLibraryFile.getAbsolutePath();
    String name = FileUtil.getNameWithoutExtension(PathUtil.getFileName(path));
    String aarDirName =
        UnpackedAarUtils.generateAarDirectoryName(name, path.hashCode()) + SdkConstants.DOT_AAR;
    UnpackedAars unpackedAars = UnpackedAars.getInstance(getProject());
    return new File(unpackedAars.getCacheDir(), aarDirName);
  }
}
