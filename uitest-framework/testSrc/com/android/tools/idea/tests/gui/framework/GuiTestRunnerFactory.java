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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.guitestsystem.TargetBuildSystem;
import com.google.common.collect.Iterables;
import com.intellij.compiler.ant.taskdefs.Target;
import org.junit.runner.Runner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * Factory for creating {@link GuiTestRunner}'s when running tests parameterized by {@link TargetBuildSystem}.
 */
public class GuiTestRunnerFactory implements ParametersRunnerFactory {
  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
    TargetBuildSystem.BuildSystem buildSystem = (TargetBuildSystem.BuildSystem) Iterables.getOnlyElement(test.getParameters());
    return new GuiTestRunner(test.getTestClass().getJavaClass(), buildSystem);
  }
}
