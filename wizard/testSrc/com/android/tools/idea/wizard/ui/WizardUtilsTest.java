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
package com.android.tools.idea.wizard.ui;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.ide.GeneralLocalSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import java.io.File;

import static com.android.tools.idea.wizard.ui.WizardUtils.getProjectLocationParent;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WizardUtilsTest {
  @Rule
  public AndroidProjectRule projectRule = AndroidProjectRule.onDisk();

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
    GeneralLocalSettings generalSettingsMock = mock(GeneralLocalSettings.class);
    when(generalSettingsMock.getDefaultProjectDirectory()).thenReturn(defaultProjectDirectory);
    projectRule.replaceService(GeneralLocalSettings.class, generalSettingsMock);
  }
}