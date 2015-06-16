/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture for the tree widget, on the left hand side of "Run" window (when running tests).
 */
public class UnitTestTreeFixture {
  private ExecutionToolWindowFixture.ContentFixture myContentFixture;
  private final TestTreeView myTreeView;

  public UnitTestTreeFixture(@NotNull ExecutionToolWindowFixture.ContentFixture contentFixture,
                             @NotNull TestTreeView treeView) {
    myContentFixture = contentFixture;
    myTreeView = treeView;
  }

  @Nullable
  public JUnitRunningModel getModel() {
    Pause.pause(new Condition("Wait for the test results model.") {
      @Override
      public boolean test() {
        return myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName()) != null;
      }
    });

    return (JUnitRunningModel)myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName());
  }

  public boolean isAllTestsPassed() {
    return !getModel().getProgress().hasDefects();
  }

  public int getFailingTestsCount() {
    return getModel().getProgress().countDefects();
  }

  public int getAllTestsCount() {
    TestProxy root = getModel().getRoot();
    if (root.getChildCount() == 0) {
      // When root has no children, it means we're only running one method, which is the root.
      return 1;
    } else {
      // Otherwise the class is the root and there's one child per method.
      return root.getChildCount();
    }
  }

  @NotNull
  public ExecutionToolWindowFixture.ContentFixture getContent() {
    return myContentFixture;
  }
}
