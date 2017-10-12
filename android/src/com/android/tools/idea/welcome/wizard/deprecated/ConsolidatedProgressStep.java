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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.welcome.install.WizardException;
import com.android.tools.idea.wizard.dynamic.AndroidStudioWizardPath;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Step to show installation progress for long running operations contributed by other paths.
 */
public class ConsolidatedProgressStep extends ProgressStep {
  private final AtomicBoolean myIsBusy = new AtomicBoolean(false);
  private final DynamicWizardHost myHost;
  private List<? extends AndroidStudioWizardPath> myPaths;

  public ConsolidatedProgressStep(@NotNull Disposable disposable, @NotNull DynamicWizardHost host) {
    super(disposable, "Downloading Components");
    myHost = host;
  }

  public void setPaths(@NotNull List<? extends AndroidStudioWizardPath> paths) {
    myPaths = paths;
  }

  @Override
  public boolean canGoNext() {
    return super.canGoNext() && !myIsBusy.get();
  }

  @Override
  protected void execute() {
    myIsBusy.set(true);
    myHost.runSensitiveOperation(getProgressIndicator(), true, new Runnable() {
      @Override
      public void run() {
        try {
          doLongRunningOperation(ConsolidatedProgressStep.this);
        }
        catch (WizardException e) {
          Logger.getInstance(getClass()).error(e);
          showConsole();
          print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
        finally {
          myIsBusy.set(false);
        }
      }
    });
  }

  private void doLongRunningOperation(@NotNull final ProgressStep progressStep) throws WizardException {
    for (AndroidStudioWizardPath path : myPaths) {
      if (progressStep.isCanceled()) {
        break;
      }
      if (path instanceof LongRunningOperationPath) {
        ((LongRunningOperationPath)path).runLongOperation();
      }
    }
  }

  @Override
  public boolean canGoPrevious() {
    return false;
  }

  @Override
  public boolean isStepVisible() {
    return myPaths != null && !myPaths.isEmpty();
  }
}
