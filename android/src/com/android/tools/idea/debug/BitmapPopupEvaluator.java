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
package com.android.tools.idea.debug;

import com.android.ddmlib.BitmapDecoder;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.render.CustomPopupFullValueEvaluator;
import com.intellij.openapi.ui.Messages;
import com.sun.jdi.Value;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.image.BufferedImage;

abstract class BitmapPopupEvaluator extends CustomPopupFullValueEvaluator<BufferedImage> {
  @Nullable private String myError;

  public BitmapPopupEvaluator(@NotNull EvaluationContextImpl evaluationContext) {
    super("\u2026 View Bitmap", evaluationContext);
  }

  @Nullable
  public BufferedImage getImage(EvaluationContextImpl evaluationContext, Value value) {
    try {
      return BitmapDecoder.getBitmap(new BitmapEvaluatorProvider(value, evaluationContext));
    }
    catch (EvaluateException e) {
      myError = "Error while evaluating expression: " + e.getMessage();
      return null;
    }
    catch (Exception e) {
      myError = "Unexpected error: " + e.getMessage();
      return null;
    }
  }

  @Override
  protected JComponent createComponent(BufferedImage image) {
    if (image == null) {
      String message = myError == null ? "Unexpected error while obtaining image" : myError;
      return new JLabel(message, Messages.getErrorIcon(), SwingConstants.CENTER);
    }
    else {
      return ImageEditorManagerImpl.createImageEditorUI(image);
    }
  }
}
