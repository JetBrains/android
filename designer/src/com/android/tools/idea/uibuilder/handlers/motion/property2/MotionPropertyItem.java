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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import com.android.tools.adtui.model.stdui.EditingSupport;
import com.android.tools.adtui.ptable2.PTableItem;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property2.api.ActionIconButton;
import com.android.tools.idea.common.property2.api.PropertyItem;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import javax.swing.Icon;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertyItem} for a property in the motion layout property editor.
 *
 * Values are retrieved and written via the {@link XmlTag} supplied with
 * the property.
 *
 * TODO: Refine the editing support including completion and error handling.
 */
public class MotionPropertyItem implements PropertyItem, PTableItem {
  private final MotionLayoutAttributesModel myModel;
  private final String myNamespace;
  private final String myName;
  private final AttributeDefinition myDefinition;
  private final SmartPsiElementPointer<XmlTag> myTagPointer;
  private final NlComponent myComponent;
  private final EditingSupport myEditingSupport;

  public MotionPropertyItem(@NotNull MotionLayoutAttributesModel model,
                            @NotNull String namespace,
                            @NotNull String name,
                            @Nullable AttributeDefinition definition,
                            @NotNull SmartPsiElementPointer<XmlTag> tagPointer,
                            @NotNull NlComponent component) {
    myModel = model;
    myNamespace = namespace;
    myName = name;
    myDefinition = definition;
    myTagPointer = tagPointer;
    myComponent = component;
    myEditingSupport = EditingSupport.Companion.getINSTANCE();
  }

  @NotNull
  @Override
  public String getNamespace() {
    return myNamespace;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  public SmartPsiElementPointer<XmlTag> getTag() {
    return myTagPointer;
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  @Nullable
  @Override
  public String getValue() {
    XmlTag tag = myTagPointer.getElement();
    if (tag == null) {
      return null;
    }
    return tag.getAttributeValue(myName, myNamespace);
  }

  @Override
  public void setValue(@Nullable String newValue) {
    XmlTag tag = myTagPointer.getElement();
    if (tag == null) {
      return;
    }
    TransactionGuard.submitTransaction(myModel, () ->
      WriteCommandAction.runWriteCommandAction(
        myModel.getProject(),
        "Set $componentName.$name to $newValue",
        null,
        () -> tag.setAttribute(myName, myNamespace, newValue)));
  }

  @Override
  public boolean isReference() {
    return false;
  }

  @Nullable
  @Override
  public ActionIconButton getBrowseButton() {
    return null;
  }

  @Nullable
  @Override
  public ActionIconButton getColorButton() {
    return null;
  }

  @Nullable
  @Override
  public Icon getNamespaceIcon() {
    return null;
  }

  @Nullable
  @Override
  public String getResolvedValue() {
    return null;
  }

  @NotNull
  @Override
  public String getTooltipForName() {
    return "";
  }

  @NotNull
  @Override
  public String getTooltipForValue() {
    return "";
  }

  @NotNull
  @Override
  public EditingSupport getEditingSupport() {
    return myEditingSupport;
  }

  @NotNull
  @Override
  public PropertyItem getDesignProperty() {
    throw new UnsupportedOperationException();
  }
}
