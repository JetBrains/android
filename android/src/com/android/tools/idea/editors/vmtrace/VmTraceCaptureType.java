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
package com.android.tools.idea.editors.vmtrace;

import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureType;
import com.android.utils.SdkUtils;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.android.ddmlib.DdmConstants.DOT_TRACE;

public class VmTraceCaptureType extends CaptureType {
  @NotNull
  @Override
  public String getName() {
    return "Method Tracing";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AndroidIcons.Ddms.StartMethodProfiling;
  }

  @Override
  public boolean isValidCapture(@NotNull VirtualFile file) {
    return SdkUtils.endsWithIgnoreCase(file.getPath(), DOT_TRACE);
  }

  @NotNull
  @Override
  public String createCaptureFileName() {
    return "Trace_" + (new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(new Date())) + DOT_TRACE;
  }

  @NotNull
  @Override
  protected Capture createCapture(@NotNull VirtualFile file) {
    return new Capture(file, this);
  }
}
