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
package com.android.tools.idea.ui.wizard;

import com.android.tools.adtui.validation.Validator.Result;
import com.android.tools.adtui.validation.Validator.Severity;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.idea.ui.wizard.WizardUtils.getProjectLocationParent;
import static com.android.tools.idea.ui.wizard.WizardUtils.getUniqueName;
import static com.android.tools.idea.ui.wizard.WizardUtils.validatePackageName;
import static com.google.common.base.Strings.repeat;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WizardUtilsTest {
  private final Result ERROR = new Result(Severity.ERROR, "Some error message");
  private final Result WARNING = new Result(Severity.WARNING, "Some warning message");
  private final Result INFO = new Result(Severity.INFO, "Some info message");

  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.onDisk();

  @Test
  public void getUniqueNameForAlwaysOKResult() {
    assertThat(getUniqueName("test", value -> OK)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForAlwaysERRORResult() {
    assertThat(getUniqueName("test", value -> ERROR)).isEqualTo("test100");
  }

  @Test
  public void getUniqueNameForAlwaysWarningResult() {
    assertThat(getUniqueName("test", value -> WARNING)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForAlwaysInfoResult() {
    assertThat(getUniqueName("test", value -> INFO)).isEqualTo("test");
  }

  @Test
  public void getUniqueNameForSomeOKResult() {
    assertThat(getUniqueName("test", value -> value.endsWith("9") ? OK : ERROR)).isEqualTo("test9");
  }

  @Test
  public void validatePackageNameWithNullPackage() {
    assertThat(validatePackageName(null)).isEqualTo("Package name is missing");
  }

  @Test
  public void validatePackageNameWithLongPackage() {
    assertThat(validatePackageName(repeat("A", 200))).isEqualTo("Package name is too long");
  }

  @Test
  public void defaultProjectLocation() {
    setRecentProjectLocation(null);
    setDefaultProjectDirectory(null);

    assertThat(getProjectLocationParent().getName()).isEqualTo(IdeInfo.getInstance().isAndroidStudio() ? "AndroidStudioProjects" : "IntelliJIDEAProjects");
  }

  @Test
  public void defaultProjectLocationSetByUser() {
    String dpd = "default/project/directory";
    setRecentProjectLocation(null);
    setDefaultProjectDirectory(dpd);

    File actualLocation = getProjectLocationParent();
    assertThat(FileUtil.toSystemIndependentName(actualLocation.toString())).isEqualTo(dpd);
  }

  @Test
  public void recentProjectLocation() {
    String rpl = "recent/project/location";
    setRecentProjectLocation(rpl);
    File actualLocation = getProjectLocationParent();
    assertThat(FileUtil.toSystemIndependentName(actualLocation.toString())).isEqualTo(rpl);
  }

  private void setRecentProjectLocation(@Nullable String recentProjectLocation) {
    RecentProjectsManager rcpMock = mock(RecentProjectsManager.class);
    when(rcpMock.getLastProjectCreationLocation()).thenReturn(recentProjectLocation);
    projectRule.replaceService(RecentProjectsManager.class, rcpMock);
  }

  private void setDefaultProjectDirectory(@Nullable String defaultProjectDirectory) {
    GeneralSettings generalSettingsMock = mock(GeneralSettings.class);
    when(generalSettingsMock.getDefaultProjectDirectory()).thenReturn(defaultProjectDirectory);
    projectRule.replaceService(GeneralSettings.class, generalSettingsMock);
  }
}