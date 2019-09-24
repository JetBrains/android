package com.android.tools.idea.databinding;

import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

// TODO(b/141498231): reënable intellij.android.databinding.tests and remove this
@RunWith(Suite.class)
@Suite.SuiteClasses(com.android.tools.idea.databinding.integration.DataBindingInspectionVerificationTest.class)
public class DataBindingInspectionVerificationTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    symlinkToIdeaHome(
      "prebuilts/studio/jdk",
      "prebuilts/studio/layoutlib",
      "prebuilts/studio/sdk",
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/databinding/testData",
      "tools/base/templates",
      "tools/idea/java"); // For the mock JDK.

    setUpOfflineRepo("tools/adt/idea/android/test_deps.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/adt/idea/databinding/testapp_deps.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime.zip", "prebuilts/tools/common/m2/repository");
  }
}
