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
package com.google.idea.blaze.android.projectsystem;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbJavaTarget.java_library;
import static com.google.idea.blaze.base.sync.data.BlazeDataStorage.WORKSPACE_MODULE_NAME;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.module.Module;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BazelModuleSystem#getResourceModuleDependencies()}. */
@RunWith(JUnit4.class)
public class BazelModuleSystemResourceModuleDependenciesTest
    extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
    MockSdkUtil.registerSdk(workspace, "27");
    workspace.createDirectory(new WorkspacePath("java/com/project"));

    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(
        BlazeModuleSystemBase.returnSimpleDirectResourceDependents, false);
    registerApplicationComponent(ExperimentService.class, experimentService);
  }

  @Test
  public void getResourceModuleDependencies_noResourceModuleDependencies() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:java_lib",
        "  //java/com/project/libs:android_lib_with_resources",
        "  //java/com/project/libs:android_lib_without_resources",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin")
            .dep(
                "//java/com/project/libs:java_lib",
                "//java/com/project/libs:android_lib_without_resources",
                "//java/com/external:android_lib_with_resources")
            .res("res"),
        java_library("//java/com/project/libs:java_lib"),
        android_library("//java/com/project/libs:android_lib_without_resources"),
        android_library("//java/com/project/libs:android_lib_with_resources").res("res"),
        android_library("//java/com/external:android_lib_with_resources").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule("java.com.project.android_bin"));
    assertThat(moduleSystem.getResourceModuleDependencies()).isEmpty();
  }

  @Test
  public void getResourceModuleDependencies_withDirectResourceModuleDependency() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:android_lib_with_resources",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin")
            .dep("//java/com/project/libs:android_lib_with_resources")
            .res("res"),
        android_library("//java/com/project/libs:android_lib_with_resources").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule("java.com.project.android_bin"));
    assertThat(moduleSystem.getResourceModuleDependencies())
        .containsExactly(getModule("java.com.project.libs.android_lib_with_resources"));
  }

  @Test
  public void getResourceModuleDependencies_withTransitiveResourceModuleDependency() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:direct",
        "  //java/com/project/libs:transitive",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin")
            .dep("//java/com/project/libs/direct:direct")
            .res("res"),
        android_library("//java/com/project/libs/direct:direct")
            .dep("//java/com/project/libs:transitive"),
        android_library("//java/com/project/libs:transitive").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule("java.com.project.android_bin"));
    assertThat(moduleSystem.getResourceModuleDependencies())
        .containsExactly(getModule("java.com.project.libs.transitive"));
  }

  /**
   * This test handles a particular scenario where a target's corresponding resource module depends
   * on another resource module, but the other resource module's target key isn't actually in the
   * dependent target's list of direct or transitive dependencies.
   *
   * <p>This can happen due to resource module merging; a strategy used to preserve a mapping of
   * resource dependencies between targets with conflicting resource package names. When multiple
   * resource modules from different targets are merged, their resources are merged but the resource
   * module's target key only corresponds to one of the contributing targets. The target who's
   * target key is used to identify the resource module is called the "canonical target". See {@link
   * BlazeAndroidWorkspaceImporter#mergeAndroidResourceModules} for more details.
   *
   * <p>In this scenario, there is a "resource dependency" between the dependent target and the
   * canonical target, even though there's no real dependency between then in the build rules.
   */
  @Test
  public void
      getResourceModuleDependencies_withTransitiveResourceModuleDependency_dependedOnAbsorbedResourceDependency() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:direct",
        "  //java/com/project/libs:canonical",
        "  //java/com/project/libs/absorbed:absorbed",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin")
            .dep("//java/com/project/libs/direct:direct")
            .res("res"),
        android_library("//java/com/project/libs/direct:direct")
            .dep("//java/com/project/libs/absorbed:absorbed"),
        android_library("//java/com/project/libs:canonical")
            .res("res")
            .setResourceJavaPackage("com.project.lib"),
        android_library("//java/com/project/libs/absorbed:absorbed")
            .res("res")
            .setResourceJavaPackage("com.project.lib"));
    runFullBlazeSync();

    // Make sure the canonical target is what we expect. This makes sure that if the canonical
    // target selection logic
    // changes, this test breaks here and not mysteriously elsewhere.
    Module canonicalModule =
        AndroidResourceModuleRegistry.getInstance(getProject())
            .getModuleContainingResourcesOf(
                TargetKey.forPlainTarget(
                    Label.create("//java/com/project/libs/absorbed:absorbed")));
    assertThat(canonicalModule).isEqualTo(getModule("java.com.project.libs.canonical"));

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule("java.com.project.android_bin"));
    assertThat(moduleSystem.getResourceModuleDependencies())
        .containsExactly(getModule("java.com.project.libs.canonical"));
  }

  @Test
  public void getResourceModuleDependencies_workspaceModuleDependsOnAllResourceModules() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:java_lib",
        "  //java/com/project/libs:android_lib_with_resources",
        "  //java/com/project/libs:android_lib_without_resources",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin").res("res"),
        java_library("//java/com/project/libs:java_lib"),
        android_library("//java/com/project/libs:android_lib_without_resources"),
        android_library("//java/com/project/libs:android_lib_with_resources").res("res"),
        android_library("//java/com/external:android_lib_with_resources").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule(WORKSPACE_MODULE_NAME));
    assertThat(moduleSystem.getResourceModuleDependencies())
        .containsExactly(
            getModule("java.com.project.android_bin"),
            getModule("java.com.project.libs.android_lib_with_resources"));
  }

  @Test
  public void getResourceModuleDependencies_nonResourceModuleDependsOnNothing() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:android_bin",
        "  //java/com/project/libs:java_lib",
        "  //java/com/project/libs:android_lib_with_resources",
        "  //java/com/project/libs:android_lib_without_resources",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_binary("//java/com/project:android_bin").res("res"),
        java_library("//java/com/project/libs:java_lib"),
        android_library("//java/com/project/libs:android_lib_without_resources"),
        android_library("//java/com/project/libs:android_lib_with_resources").res("res"),
        android_library("//java/com/external:android_lib_with_resources").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule(".project-data-dir"));
    assertThat(moduleSystem.getResourceModuleDependencies()).isEmpty();
  }

  @Test
  public void getDirectResourceModuleDependents_nothingDependsOnNonResourceModule() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:transitive",
        "  //java/com/project/direct:direct",
        "  //java/com/project/target:target",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_library("//java/com/project:transitive")
            .dep("//java/com/project/direct:direct")
            .res("res"),
        android_library("//java/com/project/direct:direct")
            .dep("//java/com/project/target:target")
            .res("res"),
        android_library("//java/com/project/target:target").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule(WORKSPACE_MODULE_NAME));
    assertThat(moduleSystem.getDirectResourceModuleDependents()).isEmpty();
  }

  @Test
  public void getDirectResourceModuleDependents_includesOnlyDirectDependents() {
    setProjectView(
        "directories:",
        "  java/com/project",
        "targets:",
        "  //java/com/project:transitive",
        "  //java/com/project/direct:direct",
        "  //java/com/project/target:target",
        "android_sdk_platform: android-27");

    setTargetMap(
        android_library("//java/com/project:transitive")
            .dep("//java/com/project/direct:direct")
            .res("res"),
        android_library("//java/com/project/direct:direct")
            .dep("//java/com/project/target:target")
            .res("res"),
        android_library("//java/com/project/target:target").res("res"));
    runFullBlazeSync();

    AndroidModuleSystem moduleSystem = getModuleSystem(getModule("java.com.project.target.target"));
    assertThat(moduleSystem.getDirectResourceModuleDependents())
        .containsExactly(getModule("java.com.project.direct.direct"));
  }
}
