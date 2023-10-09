/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.studio.samples;

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.appspot.gsamplesindex.samplesindex.SamplesIndex;
import com.appspot.gsamplesindex.samplesindex.model.SampleCollection;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import icons.SampleImportIcons;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Action that initiates the Sample Import Wizard, it will also download the samples list from the samples service and pass
 * it as a paramter to the Sample Wizard.
 */
public class SampleImportAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(SampleImportAction.class);

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(SampleImportIcons.Welcome.IMPORT_CODE_SAMPLE);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AtomicReference<SampleCollection> sampleList = new AtomicReference<SampleCollection>(null);
    SamplesIndex samplesIndex = SamplesService.getInstance().getIndex();
    new Task.Modal(null, SamplesBrowserBundle.message("sample.import.title"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(SamplesBrowserBundle.message("sample.index.downloading"));
        try {
          sampleList.set(samplesIndex.samples().listSamples().set("technology", "android").execute());
        }
        catch (IOException ex) {
          LOG.warn(SamplesBrowserBundle.message("sample.index.download.failed"));
          sampleList.set(null);
        }
      }
    }.queue();

    if (sampleList.get() == null || sampleList.get().isEmpty()) {
      Messages.showErrorDialog(SamplesBrowserBundle.message("sample.index.download.failed"), SamplesBrowserBundle.message("sample.import.error.title"));
      return;
    }

    if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
      String title = "SDK problem";
      String msg =
        "<html>Your Android SDK is missing or out of date.<br>" +
        "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      SampleModel model = new SampleModel();
      ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
      wizardBuilder.addStep(new SampleBrowserStep(model, sampleList.get()));

      ModelWizard wizard = wizardBuilder.build();
      ModelWizardDialog dialog = new StudioWizardDialogBuilder(wizard, SamplesBrowserBundle.message("sample.import.title")).build();
      dialog.show();
    });
  }
}
