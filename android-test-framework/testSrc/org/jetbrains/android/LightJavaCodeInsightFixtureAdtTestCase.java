package org.jetbrains.android;

import com.android.testutils.TestUtils;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import com.intellij.pom.java.LanguageLevel;

/**
 * A specialized version of LightJavaCodeInsightFixtureTestCase that sets up
 * a java SDK to point to a custom mock one. Use this subclass if your
 * tests need a java sdk set up.
 */
public abstract class LightJavaCodeInsightFixtureAdtTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return getAdtProjectDescriptor();
  }

  @NotNull
  public static LightProjectDescriptor getAdtProjectDescriptor() {
    return new ProjectDescriptor(LanguageLevel.HIGHEST) {
      @Override
      public Sdk getSdk() {
        String path = TestUtils.getMockJdk().getAbsolutePath();
        return IdeaTestUtil.createMockJdk("java 1.7", path);
      }
    };
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
