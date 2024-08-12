/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sdk;

import com.android.tools.idea.sdk.IdeSdks;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/** Listens for android SDK changes, and queues up a blaze sync */
public class AndroidSdkListener implements IdeSdks.AndroidSdkEventListener {

  @Override
  public void afterSdkPathChange(@NotNull File sdkPath, @NotNull Project project) {
    if (Blaze.isBlazeProject(project)) {
      BlazeSyncStatus.getInstance(project).setDirty();
    }
  }
}
