/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.editors.allocations.AllocationsEditorProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class ShowAllocationsHandler implements ClientData.IAllocationTrackingHandler {
  private static final Logger LOG = Logger.getInstance(ShowAllocationsHandler.class);

  private final Project myProject;

  public ShowAllocationsHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void onSuccess(@NotNull byte[] data, @NotNull Client client) {
    File f;
    try {
      f = FileUtil.createTempFile("ddms", AllocationsEditorProvider.DOT_ALLOC);
      FileUtil.writeToFile(f, data);
      LOG.info("AllocationTracking: Saved allocations to file: " + f.getAbsolutePath());
    }
    catch (IOException e) {
      //noinspection ThrowableResultOfMethodCallIgnored
      showError("Unexpected error while saving allocations to a temporary file: " + ExceptionUtil.getRootCause(e).getLocalizedMessage());
      return;
    }
    final VirtualFile vf = VfsUtil.findFileByIoFile(f, true);
    if (vf == null) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, vf);
        FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
      }
    });
  }

  private void showError(final String message) {
    LOG.error("Allocation Tracking: " + message);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myProject, message, "Allocation Tracker");
      }
    });
  }
}