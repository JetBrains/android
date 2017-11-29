// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.adtui.workbench;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class WorkBenchTestCase extends PlatformTestCase {
  private ComponentStack myApplicationComponentStack;
  private ComponentStack myProjectComponentStack;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myApplicationComponentStack = new ComponentStack(ApplicationManager.getApplication());
    myProjectComponentStack = new ComponentStack(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myApplicationComponentStack.restoreComponents();
      myApplicationComponentStack = null;
      myProjectComponentStack.restoreComponents();
      myProjectComponentStack = null;
    }
    finally {
      super.tearDown();
    }
  }

  public <T> void registerApplicationComponent(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerApplicationComponentImplementation(@NotNull Class<T> key, @NotNull T instance) {
    myApplicationComponentStack.registerComponentImplementation(key, instance);
  }

  public <T> void registerProjectComponent(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerComponentInstance(key, instance);
  }

  public <T> void registerProjectComponentImplementation(@NotNull Class<T> key, @NotNull T instance) {
    myProjectComponentStack.registerComponentImplementation(key, instance);
  }
}
