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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.LeakCheckerRule;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import static com.android.testutils.TestUtils.getWorkspaceFile;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  com.android.tools.idea.IdeaTestSuite.class,  // a suite mustn't contain itself
  com.android.tools.idea.rendering.RenderSecurityManagerTest.class,  // calls System.setSecurityManager
  com.android.tools.idea.testing.TestProjectPathsGeneratorTest.class, // This is for a standalone, test-only application
  com.android.tools.idea.templates.TemplateTest.CoverageChecker.class, // Inner class is used to test TemplateTest covers all templates

  // The following classes had failures when run in Bazel.
  com.android.tools.idea.gradle.project.NonAndroidGradleProjectImportingTestSuite.class,
  com.android.tools.perf.idea.gradle.project.sync.GradleSyncPerfTest.class, // Sync performance test only runs on perf buildbot
  // Require resources with spaces (HTML File template)
  // https://github.com/bazelbuild/bazel/issues/374
  com.android.tools.idea.actions.annotations.InferSupportAnnotationsTest.class,
  org.jetbrains.android.dom.CreateMissingClassFixTest.class,

  // Empty test in gradle-feature - http://b.android.com/230792
  com.android.tools.idea.editors.manifest.ManifestConflictTest.class,

  // http://b/35788260
  com.android.tools.idea.gradle.project.sync.errors.OldAndroidPluginErrorHandlerTest.class,
})
public class IdeaTestSuite extends IdeaTestSuiteBase {

  @ClassRule public static LeakCheckerRule checker = new LeakCheckerRule();

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    symlinkToIdeaHome(
        "prebuilts/tools/common/offline-m2",
        "tools/adt/idea/adt-ui/lib/libwebp",
        "tools/adt/idea/android/annotations",
        "tools/adt/idea/artwork/resources/device-art-resources",
        "tools/adt/idea/android/testData",
        "tools/adt/idea/android/lib",
        "tools/base/templates",
        "tools/idea/java",
        "prebuilts/studio/jdk",
        "prebuilts/studio/layoutlib",
        "prebuilts/studio/sdk");

    setUpOfflineRepo("tools/base/bazel/offline_repo_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/adt/idea/android/test_deps_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/adt/idea/android/android-gradle-1.5.0_repo_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime_repo.zip", "prebuilts/tools/common/m2/repository");

    // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
    System.setProperty("plugin.path", getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").getAbsolutePath());

    // Run Kotlin in-process for easier control over its JVM args.
    System.setProperty("kotlin.compiler.execution.strategy", "in-process");
  }
}
