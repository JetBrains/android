/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.rendering.tokens;

import static org.junit.Assert.assertEquals;

import com.google.idea.blaze.android.google3.qsync.InBazelTestProjects;
import com.google.idea.blaze.android.google3.qsync.testrules.QuerySyncIntegrationTestRule;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.artifact.OutputArtifactInfo;
import com.google.idea.blaze.qsync.deps.OutputGroup;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ExternalTransitiveRuntimeJarsTest {
  @Rule
  public final QuerySyncIntegrationTestRule rule = new QuerySyncIntegrationTestRule();

  @Test
  public void build() {
    // Arrange
    rule.runTest(rule.prepareTestProject(InBazelTestProjects.SIMPLE_COMPOSE), bazelTestProjectContext -> {
      var builder = bazelTestProjectContext.getQuerySyncProjectInternalServices().dependencyBuilder();
      var blazeContext = BlazeContext.create();

      // Act
      var output = builder.build(blazeContext, Set.of(Label.of("//simple_compose/main/java/com/basicapp:basic_lib")),
                                 EnumSet.of(OutputGroup.EXTERNAL_TRANSITIVE_RUNTIME_JARS));

      // Assert
      var expectedJars = pathListOf(
        "external/rules_kotlin++rules_kotlin_extensions+com_github_jetbrains_kotlin_git/lib/kotlin-stdlib.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/guava/guava/33.3.1-jre/processed_guava-33.3.1-jre.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/code/findbugs/jsr305/3.0.2/processed_jsr305-3.0.2.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/errorprone/error_prone_annotations/2.33.0/processed_error_prone_annotations-2.33.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/guava/failureaccess/1.0.2/processed_failureaccess-1.0.2.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/processed_listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/com/google/j2objc/j2objc-annotations/3.0.0/processed_j2objc-annotations-3.0.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_android_maven/v1/org/checkerframework/checker-qual/3.43.0/processed_checker-qual-3.43.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_activity_activity_compose/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_activity_activity_compose_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_foundation_foundation_layout/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_foundation_foundation_layout_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material3_material3/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material3_material3_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_runtime_runtime/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_runtime_runtime_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling_preview/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_preview_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_activity_activity_ktx/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_activity_activity_ktx_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_runtime_runtime_saveable/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_runtime_runtime_saveable_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_core_core_ktx/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_core_core_ktx_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/lifecycle/lifecycle-common/2.8.7/processed_lifecycle-common-2.8.7.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_runtime/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_runtime_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_viewmodel/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_viewmodel_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_savedstate_savedstate/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_savedstate_savedstate_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/processed_kotlin-stdlib-1.9.24.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/annotations/23.0.0/processed_annotations-23.0.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/processed_kotlinx-coroutines-core-1.7.3.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_foundation_foundation_layout_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_foundation_foundation_layout_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlin/kotlin-stdlib-common/1.8.22/processed_kotlin-stdlib-common-1.8.22.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_foundation_foundation/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_foundation_foundation_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material3_material3_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material3_material3_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material_icons_core/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_icons_core_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material_ripple/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_ripple_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_text/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_text_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_runtime_runtime_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_runtime_runtime_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling_data/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_data_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling_preview_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_preview_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_activity_activity/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_activity_activity_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_runtime_ktx/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_runtime_ktx_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_viewmodel_ktx/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_viewmodel_ktx_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_savedstate_savedstate_ktx/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_savedstate_savedstate_ktx_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_runtime_runtime_saveable_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_runtime_runtime_saveable_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/annotation/annotation/1.9.1/processed_annotation-1.9.1.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_geometry/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_geometry_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_graphics/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_graphics_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_unit/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_unit_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_util/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_util_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_core_core/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_core_core_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jspecify/jspecify/1.0.0/processed_jspecify-1.0.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/lifecycle/lifecycle-common-jvm/2.8.7/processed_lifecycle-common-jvm-2.8.7.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlinx/atomicfu/0.17.0/processed_atomicfu-0.17.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/arch/core/core-common/2.2.0/processed_core-common-2.2.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_runtime_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_runtime_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_viewmodel_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_viewmodel_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/processed_kotlinx-coroutines-core-jvm-1.7.3.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_annotation_annotation_experimental/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_annotation_annotation_experimental_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/collection/collection-jvm/1.5.0-alpha06/processed_collection-jvm-1.5.0-alpha06.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_animation_animation_core/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_animation_animation_core_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_unit_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_unit_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_util_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_util_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/collection/collection/1.5.0-alpha06/processed_collection-1.5.0-alpha06.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_animation_animation/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_animation_animation_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_foundation_foundation_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_foundation_foundation_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material_icons_core_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_icons_core_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material_ripple_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_ripple_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/lifecycle/lifecycle-common-java8/2.8.7/processed_lifecycle-common-java8-2.8.7.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_text_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_text_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.7.3/processed_kotlinx-coroutines-android-1.7.3.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_animation_animation_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_animation_animation_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_tooling_data_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_tooling_data_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_core_core_viewtree/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_core_core_viewtree_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_viewmodel_savedstate/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_viewmodel_savedstate_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_profileinstaller_profileinstaller/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_profileinstaller_profileinstaller_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/processed_listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_tracing_tracing/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_tracing_tracing_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_runtime_ktx_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_runtime_ktx_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/annotation/annotation-jvm/1.9.1/processed_annotation-jvm-1.9.1.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_autofill_autofill/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_autofill_autofill_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_customview_customview_poolingcontainer/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_customview_customview_poolingcontainer_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_emoji2_emoji2/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_emoji2_emoji2_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_runtime_compose_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_runtime_compose_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_geometry_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_geometry_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_ui_ui_graphics_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_ui_ui_graphics_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/concurrent/concurrent-futures/1.1.0/processed_concurrent-futures-1.1.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_interpolator_interpolator/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_interpolator_interpolator_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_versionedparcelable_versionedparcelable/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_versionedparcelable_versionedparcelable_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlinx/atomicfu-jvm/0.17.0/processed_atomicfu-jvm-0.17.0.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_arch_core_core_runtime/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_arch_core_core_runtime_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.8.20/processed_kotlin-stdlib-jdk8-1.8.20.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_animation_animation_core_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_animation_animation_core_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/androidx/collection/collection-ktx/1.5.0-alpha06/processed_collection-ktx-1.5.0-alpha06.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_compose_material_material_android/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_compose_material_material_android_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_livedata_core/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_livedata_core_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_startup_startup_runtime/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_startup_startup_runtime_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_lifecycle_lifecycle_process/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_lifecycle_lifecycle_process_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/_aar/androidx_graphics_graphics_path/classes_and_libs_merged.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/androidx_graphics_graphics_path_resources.jar",
        "bazel-out/k8-fastbuild/bin/external/rules_jvm_external++maven+rules_compose_maven/v1/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.8.20/processed_kotlin-stdlib-jdk7-1.8.20.jar"
      );

      var actualJars = output.getExternalTransitiveRuntimeJars().stream()
        .map(OutputArtifactInfo::getArtifactPath)
        .toList();

      assertEquals(expectedJars, actualJars);
    });
  }

  private static Object pathListOf(String... paths) {
    var fileSystem = FileSystems.getDefault();

    return Arrays.stream(paths)
      .map(fileSystem::getPath)
      .toList();
  }
}
