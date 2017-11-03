/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.projectstructure;

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectModuleFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SelectPathFixture;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;

public class DependencyTabFixture extends ProjectStructureDialogFixture {

  DependencyTabFixture(@NotNull JDialog dialog, @NotNull IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  @NotNull
  public DependencyTabFixture addJarDependency(@NotNull File jarFile) {
    clickAddButtonImpl();
    new JListFixture(robot(), waitForPopup(robot())).clickItem("Jar dependency");

    SelectPathFixture.find(getIdeFrameFixture())
      .selectPath(jarFile)
      .clickOK();

    return this;
  }

  @NotNull
  public DependencyTabFixture addModuleDependency(@NotNull String moduleName) {
    clickAddButtonImpl();
    new JListFixture(robot(), waitForPopup(robot())).clickItem("Module dependency");

    SelectModuleFixture.find(getIdeFrameFixture())
      .selectModule(moduleName)
      .clickOK();

    return this;
  }

}