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

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestTreeView;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
  public TestFrameworkRunningModel getModel() {
    Pause.pause(new Condition("Wait for the test results model.") {
      @Override
      public boolean test() {
        return myTreeView.getData(TestTreeView.MODEL_DATA_KEY.getName()) != null;
      }
    });

    return TestTreeView.MODEL_DATA_KEY.getData(myTreeView);
  }

  public boolean isAllTestsPassed() {
    return !getModel().getRoot().isDefect();
  }

  public long getFailingTestsCount() {
    List<? extends AbstractTestProxy> children = getModel().getRoot().getChildren();
    return children.stream().filter(Filter.DEFECTIVE_LEAF::shouldAccept).count();
  }

  public int getAllTestsCount() {
    AbstractTestProxy root = getModel().getRoot();
    List<? extends AbstractTestProxy> children = root.getChildren();
    if (children.isEmpty()) {
      // When root has no children, it means we're only running one method, which is the root.
      return 1;
    } else {
      // Otherwise the class is the root and there's one child per method.
      return children.size();
    }
  }

  @NotNull
  public ExecutionToolWindowFixture.ContentFixture getContent() {
    return myContentFixture;
  }
}
