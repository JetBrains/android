/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.sun.jdi.ObjectReference;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.Nullable;

public class BitmapDrawableRenderer extends CompoundReferenceRenderer implements FullValueEvaluatorProvider {
  public static final String BITMAP_DRAWABLE_FQCN = "android.graphics.drawable.BitmapDrawable";

  public BitmapDrawableRenderer() {
    super("BitmapDrawable", null, null);
    setClassName(BITMAP_DRAWABLE_FQCN);
    setEnabled(true);
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(EvaluationContextImpl evaluationContext, final ValueDescriptorImpl valueDescriptor) {
    if (StudioFlags.USE_BITMAP_POPUP_EVALUATOR_V2.get()) {
      return new BitmapPopupEvaluatorV2(evaluationContext, (ObjectReference)valueDescriptor.getValue());
    }
    else {
      return new BitmapPopupEvaluator(evaluationContext) {
        @Override
        protected BufferedImage getData() {
          return getImage(myEvaluationContext, valueDescriptor.getValue());
        }
      };
    }
  }
}