/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ideinfo;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Utility class to intern frequently duplicated objects in the project data.
 *
 * <p>The underlying interners are application-wide, not specific to a project.
 */
public final class ProjectDataInterner {
  private static final BoolExperiment internProjectData =
      new BoolExperiment("intern.project.data", true);

  private static volatile State state = useInterner() ? new Impl() : new NoOp();

  private static boolean useInterner() {
    return ApplicationManager.getApplication() == null
        || ApplicationManager.getApplication().isUnitTestMode()
        || internProjectData.getValue();
  }

  public static Label intern(Label label) {
    return state.doIntern(label);
  }

  private interface State {
    Label doIntern(Label label);

    String doIntern(String string);
  }

  private static class NoOp implements State {
    @Override
    public Label doIntern(Label label) {
      return label;
    }

    @Override
    public String doIntern(String string) {
      return string;
    }
  }

  private static class Impl implements State {
    private final Interner<Label> labelInterner = Interners.newWeakInterner();
    private final Interner<String> stringInterner = Interners.newWeakInterner();

    @Override
    public Label doIntern(Label label) {
      return labelInterner.intern(label);
    }

    @Override
    public String doIntern(String string) {
      return stringInterner.intern(string);
    }
  }
}
