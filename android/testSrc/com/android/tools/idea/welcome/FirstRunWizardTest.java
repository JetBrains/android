/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.ScopedStateStore.Key;
import com.android.tools.idea.wizard.SingleStepPath;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public final class FirstRunWizardTest extends AndroidTestBase {
  public static final Key<Boolean> KEY_TRUE = ScopedStateStore.createKey("true", ScopedStateStore.Scope.WIZARD, Boolean.class);
  public static final Key<Boolean> KEY_FALSE = ScopedStateStore.createKey("false", ScopedStateStore.Scope.WIZARD, Boolean.class);
  public static final Key<Boolean> KEY_MISSING = ScopedStateStore.createKey("missing", ScopedStateStore.Scope.WIZARD, Boolean.class);
  public static final Key<Integer> KEY_INTEGER = ScopedStateStore.createKey("42", ScopedStateStore.Scope.WIZARD, Integer.class);

  @NotNull
  private static File getPathChecked(String variable) {
    String path = System.getenv(variable);
    if (StringUtil.isEmpty(path)) {
      throw new IllegalStateException("Missing " + variable + " environment variable");
    }
    return new File(path);
  }

  private static <T> Key<T> createKey(Class<T> clazz) {
    return ScopedStateStore.createKey(clazz.getName(), ScopedStateStore.Scope.STEP, clazz);
  }

  @NotNull
  public static File getAndroidHome() {
    return getPathChecked("ANDROID_HOME");
  }

  private void assertPagesVisible(@Nullable InstallerData data,
                                  boolean isWelcomeStepVisible,
                                  boolean isJdkStepVisible,
                                  boolean isInstallTypeStepVisible,
                                  boolean isComponentsStepVisible,
                                  boolean hasJdkPath,
                                  boolean hasAndroidSdkPath) {
    InstallerData.set(data);
    FirstRunWizardMode mode = data == null ? FirstRunWizardMode.NEW_INSTALL : FirstRunWizardMode.INSTALL_HANDOFF;
    if (mode != FirstRunWizardMode.INSTALL_HANDOFF) {
      assertVisible(new FirstRunWelcomeStep(false), null, isWelcomeStepVisible);
    }
    assertVisible(new JdkLocationStep(createKey(String.class), mode), data, isJdkStepVisible);
    assertVisible(new InstallationTypeWizardStep(createKey(Boolean.class)), data, isInstallTypeStepVisible);
    assertVisible(new SdkComponentsStep(new InstallableComponent[0], KEY_TRUE, createKey(String.class), mode),
                  data, isComponentsStepVisible);

    if (data != null) {
      assertEquals(String.valueOf(data), hasJdkPath, data.hasValidJdkLocation());
      assertEquals(String.valueOf(data.toString()), hasAndroidSdkPath, data.hasValidSdkLocation());
    }
  }

  private void assertVisible(DynamicWizardStep step, @Nullable InstallerData data, boolean expected) {
    assertEquals(String.format("Step: %s, data: %s", step.getClass(), data), expected, isStepVisible(step));
  }

  public boolean isStepVisible(@NotNull DynamicWizardStep step) {
    SingleStepWizard wizard = new SingleStepWizard(step);
    disposeOnTearDown(wizard.getDisposable());
    wizard.init();
    return step.isStepVisible();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testStepsVisibility() {
    File wrongPath = new File("/$@@  \"\'should/not/exist");
    File java6Home = getPathChecked("JAVA6_HOME");
    File java7Home = getPathChecked("JAVA7_HOME");
    File androidHome = getAndroidHome();

    assertPagesVisible(null, true, true, true, true, false, false);

    InstallerData correctData = new InstallerData(java7Home, null, androidHome, true, "timestamp");
    assertPagesVisible(correctData, false, false, false, false, true, true);

    InstallerData java6Data = new InstallerData(java6Home, null, androidHome, true, "timestamp");
    assertPagesVisible(java6Data, false, true, false, false, false, true);

    InstallerData noAndroidSdkData = new InstallerData(java7Home, null, null, true, "timestamp");
    assertPagesVisible(noAndroidSdkData, false, false, false, true, true, false);

    InstallerData noJdkData = new InstallerData(null, null, androidHome, true, "timestamp");
    assertPagesVisible(noJdkData, false, true, false, false, false, true);

    InstallerData noInstallAndroidData = new InstallerData(java7Home, androidHome, androidHome, true, "timestamp");
    assertPagesVisible(noInstallAndroidData, false, false, false, false, true, true);

    InstallerData bogusPathsData = new InstallerData(wrongPath, wrongPath, wrongPath, true, "timestamp");
    assertPagesVisible(bogusPathsData, false, true, false, true, false, false);
  }

  /**
   * Wizard for testing a single step.
   */
  private static final class SingleStepWizard extends DynamicWizard {
    @NotNull private final DynamicWizardStep myStep;

    public SingleStepWizard(@NotNull DynamicWizardStep step) {
      super(null, null, "Single Step Wizard");
      myStep = step;
    }

    @Override
    public void init() {
      myState.put(KEY_TRUE, true);
      myState.put(KEY_FALSE, false);
      myState.put(KEY_INTEGER, 42);
      addPath(new SingleStepPath(myStep));
      // Need to have at least one visible step
      addPath(new SingleStepPath(new DynamicWizardStep() {
        private final JLabel myLabel = new JLabel();

        @Override
        public void init() {

        }

        @NotNull
        @Override
        public JComponent getComponent() {
          return myLabel;
        }

        @Nullable
        @Override
        public JLabel getMessageLabel() {
          return myLabel;
        }

        @NotNull
        @Override
        public String getStepName() {
          return "Always visible wizard step";
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myLabel;
        }
      }));
      super.init();
    }

    @Override
    public void performFinishingActions() {
      // Nothing.
    }

    @Override
    protected String getWizardActionDescription() {
      return "Test Wizard";
    }
  }
}