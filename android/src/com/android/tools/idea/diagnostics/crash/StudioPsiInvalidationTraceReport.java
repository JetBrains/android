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
package com.android.tools.idea.diagnostics.crash;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

public class StudioPsiInvalidationTraceReport extends BaseStudioReport {
  private final String invalidationReason;
  private final Throwable currentStack;
  private final String threadDump;

  public StudioPsiInvalidationTraceReport(@NonNull PsiFile psiFile, @NonNull String threadDump) {
    super(null, null, "PsiInvalidationTrace");
    this.invalidationReason = getInvalidationReason(psiFile);
    this.currentStack = new Throwable();
    this.threadDump = threadDump;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO, Throwables.getStackTraceAsString(currentStack));
    builder.addTextBody("invalidationReason", invalidationReason, ContentType.create("text/plain", Charsets.UTF_8));
    builder.addTextBody("threadDump", threadDump, ContentType.create("text/plain", Charsets.UTF_8));
  }

  private static String getInvalidationReason(PsiFile psiFile) {
    StringBuilder sb = new StringBuilder();

    sb.append(PsiInvalidElementAccessException.getInvalidationTrace(psiFile));
    sb.append("\n");

    // Extra details about the underlying file
    FileViewProvider provider = psiFile.getViewProvider();
    VirtualFile vFile = provider.getVirtualFile();
    if (!vFile.isValid()) {
      sb.append("Virtual file is invalid. ");
    }
    sb.append("\n");
    return sb.toString();
  }
}
