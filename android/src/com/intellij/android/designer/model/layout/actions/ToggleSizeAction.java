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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class ToggleSizeAction extends LayoutAction {
  private final List<? extends RadViewComponent> myComponents;
  private final String myAttribute;
  private final Icon myFillIcon;
  private final Icon myWrapIcon;

  public ToggleSizeAction(@NotNull DesignerEditorPanel designer,
                          @NotNull List<? extends RadViewComponent> components,
                          @NotNull String label,
                          @NotNull String attribute,
                          @NotNull Icon fillIcon,
                          @NotNull Icon wrapIcon) {
    super(designer, label, null, null);
    myComponents = components;
    myAttribute = attribute;
    myFillIcon = fillIcon;
    myWrapIcon = wrapIcon;
    updateLabel(getTemplatePresentation(), isFill());
  }

  private boolean isFill() {
    XmlTag tag = myComponents.get(0).getTag();
    String value = tag.getAttributeValue(myAttribute, ANDROID_URI);
    return VALUE_MATCH_PARENT.equals(value) || VALUE_FILL_PARENT.equals(value);
  }

  private void updateLabel(Presentation presentation, boolean isFill) {

    // The icon shows what we're currently set to
    presentation.setIcon(isFill ? myFillIcon : myWrapIcon);

    // The label says what the action will do:
    String text = String.format("Set %1$s to %2$s", myAttribute, isFill ? VALUE_WRAP_CONTENT : VALUE_MATCH_PARENT);
    presentation.setDescription(text);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    boolean isFill = isFill();
    updateLabel(e.getPresentation(), !isFill);
    super.actionPerformed(e);
  }

  @Override
  protected void performWriteAction() {
    boolean isFill = isFill();
    for (RadViewComponent component : myComponents) {
      XmlTag tag = component.getTag();
      if (isFill) {
        tag.setAttribute(myAttribute, ANDROID_URI, VALUE_WRAP_CONTENT);
      } else {
        // TODO: Worry about using FILL_PARENT on older platforms?
        tag.setAttribute(myAttribute, ANDROID_URI, VALUE_MATCH_PARENT);
      }
    }
  }
}
