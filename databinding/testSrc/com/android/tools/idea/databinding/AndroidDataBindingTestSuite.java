/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  AndroidDataBindingTestSuite.class,  // a suite mustn't contain itself
})
public final class AndroidDataBindingTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    linkIntoOfflineMavenRepo("tools/adt/idea/android/test_deps.manifest");
    linkIntoOfflineMavenRepo("tools/adt/idea/databinding/testapp_deps.manifest");
    linkIntoOfflineMavenRepo("tools/base/build-system/studio_repo.manifest");
    unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip");
  }
}
