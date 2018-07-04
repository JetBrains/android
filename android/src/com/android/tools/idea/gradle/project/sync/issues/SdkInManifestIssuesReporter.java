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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.RemoveSdkFromManifestHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.android.facet.AndroidRootUtil.getPrimaryManifestFile;

/**
 * Handles the sync issue when min sdk version is defined in manifest files.
 */
public class SdkInManifestIssuesReporter extends BaseSyncIssuesReporter {

  @Override
  int getSupportedIssueType() {
    return SyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST;
  }

  @Override
  void report(@NotNull SyncIssue syncIssue, @NotNull Module module, @Nullable VirtualFile buildFile) {
    SyncMessage message = generateSyncMessage(syncIssue, module, buildFile);
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      VirtualFile manifest = getPrimaryManifestFile(androidFacet);
      if (manifest != null) {
        message.add(new OpenFileHyperlink(manifest.getPath(), "Open Manifest File", -1, -1));
        message.add(new RemoveSdkFromManifestHyperlink(module));
      }
      getSyncMessages(module).report(message);
    }
  }
}
