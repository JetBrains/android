package org.jetbrains.android;

import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.tests.AdtTestProjectDescriptors;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A specialized version of LightJavaCodeInsightFixtureTestCase that sets up
 * a java SDK to point to a custom mock one. Use this subclass if your
 * tests need a java sdk set up.
 */
public abstract class LightJavaCodeInsightFixtureAdtTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return AdtTestProjectDescriptors.defaultDescriptor();
  }

  @Override
  protected void tearDown() throws Exception {
    List<Sdk> sdks = AndroidSdks.getInstance().getAllAndroidSdks();
    for (Sdk sdk : sdks) {
      WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().removeJdk(sdk));
    }
    super.tearDown();
  }
}
