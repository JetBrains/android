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
package com.intellij.android.designer.model.layout.actions;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ThrowableRunnable;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;

public class AssignWeightAction extends LayoutAction {
  private final RadViewComponent myLayout;
  private final List<? extends RadViewComponent> mySelectedChildren;

  public AssignWeightAction(@NotNull DesignerEditorPanel designer,
                            @NotNull RadViewComponent layout,
                            @NotNull List<? extends RadViewComponent> selectedChildren) {
    super(designer, "Change Layout Weight", null, AndroidDesignerIcons.Weights);
    myLayout = layout;
    mySelectedChildren = selectedChildren;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final List<? extends RadViewComponent> targets = mySelectedChildren;
    if (targets.isEmpty()) {
      return;
    }
    String currentWeight = targets.get(0).getTag().getAttributeValue(ATTR_LAYOUT_WEIGHT, ANDROID_URI);
    if (currentWeight == null || currentWeight.isEmpty()) {
      currentWeight = "0.0";
    }

    String title = getTemplatePresentation().getDescription();
    InputValidator validator = null; // TODO: Number validation!
    final String weight = Messages.showInputDialog(myDesigner, "Enter Weight Value:", title, null, currentWeight, validator);
    if (weight != null) {

      myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
        @Override
        public void run() throws Exception {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              if (weight.isEmpty()) {
                // Remove attributes
                ClearWeightsAction.clearWeights(myLayout, targets);
              } else {
                for (RadViewComponent target : targets) {
                  target.getTag().setAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI, weight);
                }
              }
            }
          });
        }
      }, getTemplatePresentation().getDescription(), true);
    }
  }

  @Override
  protected void performWriteAction() {
    // We want to open the UI input dialog outside the write lock, so we handle the locking on our own in actionPerformed
    assert false;
  }
}
