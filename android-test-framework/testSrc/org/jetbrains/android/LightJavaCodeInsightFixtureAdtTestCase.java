package org.jetbrains.android;

import com.android.tools.tests.AdtTestProjectDescriptors;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Like {@link LightJavaCodeInsightFixtureTestCase} but uses a local mock JDK instead
 * of downloading one from the network. This allows tests to run in Bazel where
 * network access is generally disallowed.
 * <p>
 * If you do not need a mock JDK attached to your test project, consider using
 * {@link com.intellij.testFramework.fixtures.BasePlatformTestCase} instead.
 */
public abstract class LightJavaCodeInsightFixtureAdtTestCase extends LightJavaCodeInsightFixtureTestCase {

  @Override
  @NotNull
  protected LightProjectDescriptor getProjectDescriptor() {
    return AdtTestProjectDescriptors.defaultDescriptor();
  }
}
