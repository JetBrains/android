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
package com.android.tools.idea.lint;

import com.android.tools.idea.rendering.webp.ConvertFromWebpAction;
import com.android.tools.lint.checks.IconDetector;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;

public class AndroidLintWebpUnsupportedInspection extends AndroidLintInspectionBase {
  public AndroidLintWebpUnsupportedInspection() {
    super(AndroidBundle.message("android.lint.inspections.webp.unsupported"), IconDetector.WEBP_UNSUPPORTED);
  }

  @Override
  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new AndroidLintQuickFix[]{
      new DefaultLintQuickFix("Convert image to PNG") {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          VirtualFile file = startElement.getContainingFile().getVirtualFile();
          if (file.exists()) {
            new ConvertFromWebpAction().perform(startElement.getProject(), new VirtualFile[] {file }, true);
            WriteCommandAction.runWriteCommandAction(startElement.getProject(), () -> {
              try {
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image != null) {
                  ByteArrayOutputStream stream = new ByteArrayOutputStream(2048);
                  ImageIO.write(image, "PNG", stream);
                  byte[] encoded = stream.toByteArray();
                  VirtualFile folder = file.getParent();
                  VirtualFile output = folder.createChildData(this, file.getNameWithoutExtension() + DOT_PNG);
                  try (OutputStream fos = new BufferedOutputStream(output.getOutputStream(this))) {
                    fos.write(encoded);
                  }
                  file.delete(this);
                  folder.refresh(true, true);
                }
              }
              catch (IOException e) {
                Logger.getInstance(AndroidLintWebpUnsupportedInspection.class).warn(e.getMessage());
              }
            });
          }
        }
      }
    };
  }
}