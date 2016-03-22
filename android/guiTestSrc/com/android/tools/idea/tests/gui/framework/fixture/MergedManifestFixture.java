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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.editors.manifest.ManifestPanel;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

public class MergedManifestFixture extends ComponentFixture<MergedManifestFixture, ManifestPanel>{

  private @NotNull JTreeFixture myTree;

  public MergedManifestFixture(@NotNull Robot robot, @NotNull ManifestPanel manifestComponent) {
    super(MergedManifestFixture.class, robot, manifestComponent);
    myTree = new JTreeFixture(robot(), robot().finder()
      .findByType(this.target(), Tree.class, true));
  }

  @NotNull
  public JTreeFixture getTree() {
    return myTree;
  }
}
