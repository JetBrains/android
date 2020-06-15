/*
 * Copyright (C) 2019 The Android Open Source Project
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

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  OldAgpTestSuite.class,  // a suite mustn't contain itself
})
public class OldAgpTestSuite extends IdeaTestSuiteBase {

  @ClassRule public static LeakCheckerRule checker = new LeakCheckerRule();

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    symlinkToIdeaHome(
      "prebuilts/studio/jdk",
      "prebuilts/studio/layoutlib",
      "prebuilts/studio/sdk",
      "tools/adt/idea/adt-ui/lib/libwebp",
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/android/lib",
      "tools/adt/idea/artwork/resources/device-art-resources",
      "tools/adt/idea/android/testData",
      "tools/base/templates",
      "tools/idea/java"
    );

    setUpOfflineRepo("tools/adt/idea/android/test_deps.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/previous-versions/1.5.0.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/previous-versions/2.2.0.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/previous-versions/3.0.0.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/previous-versions/3.3.2.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/previous-versions/3.5.0.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/base/third_party/kotlin/kotlin-m2repository.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime.zip", "prebuilts/tools/common/m2/repository");
  }
}
