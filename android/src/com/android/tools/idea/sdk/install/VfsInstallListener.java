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
package com.android.tools.idea.sdk.install;

//import static com.android.tools.idea.sdk.SdksCleanupUtil.updateSdkIfNeeded;

import com.android.annotations.NonNull;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;

/**
 * {@link PackageOperation.StatusChangeListener} that refreshes the VFS and {@link Sdk}s when a change is complete.
 */
public class VfsInstallListener implements PackageOperation.StatusChangeListener {
  @Override
  public void statusChanged(@NonNull PackageOperation op, @NonNull ProgressIndicator progress) {
    if (op.getInstallStatus().equals(PackageOperation.InstallStatus.COMPLETE)) {
      new Task.Backgroundable(null, "Refreshing...", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          doRefresh(op, progress);
        }
      }.queue();
    }
  }

  private static void doRefresh(@NonNull PackageOperation op,
                                @NonNull ProgressIndicator progress) {
    // We must refreshIfNeeded otherwise directories that are added will never be refreshed
    VirtualFile file = VfsUtil.findFile(op.getLocation(progress), true);
    if (file != null) {
      file.refresh(false, true);
    }
    // Note that this must be done asynchronously, and must be done separately from the refresh above:
    // The above refresh may note that jar files are added or deleted, which then kicks off an asynchronous
    // refresh of jar-rooted filesystems. We queue a dummy refresh to run afterward, with our desired logic
    // as the finishRunnable.
    /*
    RefreshQueue.getInstance().refresh(true, false, () -> {
      AndroidSdks androidSdks = AndroidSdks.getInstance();
      for (Sdk sdk : androidSdks.getAllAndroidSdks()) {
        updateSdkIfNeeded(sdk, androidSdks);
      }
    });*/
  }
}
