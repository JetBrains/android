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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.components.JBList;
import javax.swing.JDialog;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class NewModuleWizardFixture extends AbstractWizardFixture<NewModuleWizardFixture> {

  public static NewModuleWizardFixture find(IdeFrameFixture ideFrame) {
    JDialog dialog = waitUntilShowing(ideFrame.robot(), Matchers.byTitle(JDialog.class, message("android.wizard.module.new.module.title")));
    return new NewModuleWizardFixture(ideFrame, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;

  private NewModuleWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    super(NewModuleWizardFixture.class, ideFrameFixture.robot(), dialog);
    myIdeFrameFixture = ideFrameFixture;
  }

  @NotNull
  public NewModuleWizardFixture chooseActivity(String activity) {
    new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class)).clickItem(activity);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextPhoneAndTabletModule() {
    clickNextToStep(message("android.wizard.module.new.mobile"), message("android.wizard.module.new.mobile"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextAutomotiveModule() {
    clickNextToStep(message("android.wizard.module.new.automotive"), message("android.wizard.module.new.automotive"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextWearModule() {
    clickNextToStep(message("android.wizard.module.new.wear"), message("android.wizard.module.new.wear"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextAndroidTvModule() {
    clickNextToStep(message("android.wizard.module.new.tv"), message("android.wizard.module.new.tv"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture> clickNextToDynamicFeature() {
    clickNextToStep(message("android.wizard.module.new.dynamic.module"), message("android.wizard.module.config.title"));
    return new ConfigureDynamicFeatureStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture> clickNextToInstantDynamicFeature() {
    clickNextToStep(message("android.wizard.module.new.dynamic.module.instant"), message("android.wizard.module.config.title"));
    return new ConfigureDynamicFeatureStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextToAndroidLibrary() {
    clickNextToStep(message("android.wizard.module.new.library"), message("android.wizard.module.new.library"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureLibraryStepFixture<NewModuleWizardFixture> clickNextToPureLibrary() {
    clickNextToStep(
      message("android.wizard.module.new.java.or.kotlin.library"),
      message("android.wizard.module.new.java.or.kotlin.library")
    );
    return new ConfigureLibraryStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ChooseBenchmarkModuleTypeStepFixture<NewModuleWizardFixture> clickNextToChooseBenchmarkModuleType() {
    clickNextToStep(
      message("android.wizard.module.new.benchmark.module.app"),
      message("android.wizard.module.new.benchmark.module.app")
    );
    return new ChooseBenchmarkModuleTypeStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNewNativeLibraryModule() {
    clickNextToStep(message("android.wizard.module.new.native.library"), message("android.wizard.module.new.native.library"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  private void clickNextToStep(String moduleName, String nextStepTitle) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), JBList.class));
    listFixture.replaceCellReader((list, index) -> ((ModuleGalleryEntry)list.getModel().getElementAt(index)).getName());
    listFixture.clickItem(moduleName);
  }

  private void clickFinish() {
    super.clickFinish(Wait.seconds(10));
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish() {
    return myIdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(it -> clickFinish());
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish(@NotNull Wait waitSync) {
    return myIdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(waitSync, it -> clickFinish());
  }
}
