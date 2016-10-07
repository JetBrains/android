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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class SetFirebaseLogTagsTask implements LaunchTask {
  @NotNull
  @Override
  public String getDescription() {
    return "Setting Firebase Log Properties on the device";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.ASYNC_TASK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        NullOutputReceiver receiver = new NullOutputReceiver();
        device.executeShellCommand("setprop log.tag.FA VERBOSE", receiver);
        device.executeShellCommand("setprop log.tag.FA-SVC VERBOSE", receiver);
      }
      catch (Exception ignored) {
      }
    });
    return true;
  }

  public static boolean projectUsesFirebase(@NotNull AndroidFacet facet) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model == null) {
      return false;
    }

    return GradleUtil.dependsOn(model, "com.google.android.gms:firebase-measurement") ||
           GradleUtil.dependsOn(model, "com.google.firebase:firebase-measurement") ||
           GradleUtil.dependsOn(model, "com.google.firebase:firebase-analytics");
  }
}
