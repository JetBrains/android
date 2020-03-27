/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import icons.StudioIcons;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

final class AdbConnectionStatusAction extends AnAction implements CustomComponentAction {
  @NotNull
  private final BooleanSupplier myAdbConnectionStatusActionVisibleGet;

  @NotNull
  private final Function<Project, File> myGetAdb;

  @NotNull
  private final Function<File, Future<AndroidDebugBridge>> myGetDebugBridge;

  @SuppressWarnings("unused")
  private AdbConnectionStatusAction() {
    this(() -> StudioFlags.ADB_CONNECTION_STATUS_ACTION_VISIBLE.get(), AndroidSdkUtils::getAdb, AdbService.getInstance()::getDebugBridge);
  }

  @VisibleForTesting
  @NonInjectable
  AdbConnectionStatusAction(@NotNull BooleanSupplier adbConnectionStatusActionVisibleGet,
                            @NotNull Function<Project, File> getAdb,
                            @NotNull Function<File, Future<AndroidDebugBridge>> getDebugBridge) {
    myAdbConnectionStatusActionVisibleGet = adbConnectionStatusActionVisibleGet;
    myGetAdb = getAdb;
    myGetDebugBridge = getDebugBridge;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (!myAdbConnectionStatusActionVisibleGet.getAsBoolean()) {
      presentation.setVisible(false);
      return;
    }

    presentation.setVisible(true);
    File adb = myGetAdb.apply(event.getProject());

    if (adb == null) {
      presentation.setIcon(StudioIcons.Common.ERROR);

      // noinspection DialogTitleCapitalization
      presentation.setDescription("adb executable not found");

      return;
    }

    Future<AndroidDebugBridge> future = myGetDebugBridge.apply(adb);

    if (!future.isDone()) {
      presentation.setIcon(StudioIcons.Common.ERROR);
      presentation.setDescription("Connecting to adb...");

      return;
    }

    if (future.isCancelled()) {
      presentation.setIcon(StudioIcons.Common.ERROR);
      presentation.setDescription("Connecting to adb...");

      return;
    }

    try {
      future.get();

      presentation.setIcon(StudioIcons.Common.SUCCESS);
      presentation.setDescription("Connected to adb");
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();

      presentation.setIcon(StudioIcons.Common.ERROR);
      presentation.setDescription("Connecting to adb...");
    }
    catch (ExecutionException exception) {
      presentation.setIcon(StudioIcons.Common.ERROR);
      presentation.setDescription("Connecting to adb...");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    JLabel label = new JLabel(presentation.getIcon());
    label.setToolTipText(presentation.getDescription());

    presentation.addPropertyChangeListener(event -> {
      switch (event.getPropertyName()) {
        case Presentation.PROP_ICON:
          label.setIcon((Icon)event.getNewValue());
          break;
        case Presentation.PROP_DESCRIPTION:
          label.setToolTipText((String)event.getNewValue());
          break;
        default:
          break;
      }
    });

    return label;
  }
}
