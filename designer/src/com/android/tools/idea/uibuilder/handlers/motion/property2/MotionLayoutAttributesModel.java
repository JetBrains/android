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

import static com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutPropertyProvider.mapToCustomType;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel extends NelePropertiesModel {

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable, @NotNull AndroidFacet facet) {
    super(parentDisposable, new MotionLayoutPropertyProvider(facet), facet, false);
  }

  @Override
  @Nullable
  public String getPropertyValue(@NotNull NelePropertyItem property) {
    XmlTag tag = getTag(property);
    if (tag == null) {
      return null;
    }
    NlComponent component = getComponent(property);
    if (component == null ) {
      return null;
    }
    NlComponentDelegate delegate = component.getDelegate();
    if (delegate != null && delegate.handlesAttribute(component, property.getNamespace(), property.getName())) {
      return component.getLiveAttribute(property.getNamespace(), property.getName());
    }
    if (tag.getLocalName().equals(MotionSceneString.KeyAttributes_customAttribute)) {
      return tag.getAttributeValue(mapToCustomType(property.getType()), SdkConstants.AUTO_URI);
    }
    return tag.getAttributeValue(property.getName(), property.getNamespace());
  }

  @Override
  public void setPropertyValue(@NotNull NelePropertyItem property, @Nullable String newValue) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () -> {
      XmlTag tag = getTag(property);
      NlComponent component = getComponent(property);
      if (tag != null && component != null) {
        WriteCommandAction.runWriteCommandAction(
          getFacet().getModule().getProject(),
          "Set $componentName.$name to $newValue",
          null,
          () -> setPropertyValue(tag, component, property, newValue));
      }
    });
  }

  @Nullable
  private static NlComponent getComponent(@NotNull NelePropertyItem property) {
    List<NlComponent> components = property.getComponents();
    return components.isEmpty() ? null : components.get(0);
  }

  private static void setPropertyValue(@NotNull XmlTag tag,
                                       @NotNull NlComponent component,
                                       @NotNull NelePropertyItem property,
                                       @Nullable String newValue) {
    NlComponentDelegate delegate = component.getDelegate();
    component.clearTransaction();
    if (delegate != null && delegate.handlesAttribute(component, property.getNamespace(), property.getName())) {
      component.setAttribute(property.getNamespace(), property.getName(), newValue);
      return;
    }
    if (tag.getLocalName().equals(MotionSceneString.KeyAttributes_customAttribute)) {
      tag.setAttribute(mapToCustomType(property.getType()), SdkConstants.AUTO_URI, newValue);
    }
    else {
      tag.setAttribute(property.getName(), property.getNamespace(), newValue);
    }
    MotionSceneModel.saveAndNotify(tag.getContainingFile(), property.getComponents().get(0).getModel());
  }

  public void createCustomXmlTag(@NotNull XmlTag keyFrameOrConstraint,
                                 @NotNull String attrName,
                                 @NotNull String value,
                                 @NotNull MotionSceneModel.CustomAttributes.Type type,
                                 @NotNull Consumer<XmlTag> operation) {
    List<XmlTag> oldTags = Arrays.stream(keyFrameOrConstraint.findSubTags(MotionSceneString.KeyAttributes_customAttribute))
      .filter(tag -> attrName.equals(tag.getAttribute(MotionSceneString.CustomAttributes_attributeName, SdkConstants.AUTO_URI)))
      .collect(Collectors.toList());

    Runnable transaction = () -> {
      oldTags.forEach(tag -> tag.delete());

      XmlTag createdTag = keyFrameOrConstraint.createChildTag(MotionSceneString.KeyAttributes_customAttribute, null, null, false);
      createdTag = keyFrameOrConstraint.addSubTag(createdTag, false);
      createdTag.setAttribute(MotionSceneString.CustomAttributes_attributeName, SdkConstants.AUTO_URI, attrName);
      createdTag.setAttribute(type.getTagName(), SdkConstants.AUTO_URI, StringUtil.isNotEmpty(value) ? value : type.getDefaultValue());

      operation.accept(createdTag);
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        "Set $componentName.$name to $newValue",
        null,
        transaction,
        keyFrameOrConstraint.getContainingFile()));
  }

  public void deleteTag(@NotNull XmlTag tag, @NotNull Runnable operation) {
    PsiFile file = tag.getContainingFile();
    Runnable transaction = () -> {
      tag.delete();
      operation.run();
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        "Set $componentName.$name to $newValue",
        null,
        transaction,
        file));
  }

  @Override
  protected boolean wantComponentSelectionUpdate(@Nullable DesignSurface surface,
                                                 @Nullable DesignSurface activeSurface,
                                                 @Nullable AccessoryPanelInterface activePanel) {
    return false;
  }

  @Override
  protected boolean wantPanelSelectionUpdate(@NotNull AccessoryPanelInterface panel, @Nullable AccessoryPanelInterface activePanel) {
    return panel == activePanel && panel.getSelectedAccessory() != null && panel instanceof GanttEventListener;
  }

  @Nullable
  public static XmlTag getTag(@NotNull NelePropertyItem property) {
    @SuppressWarnings("unchecked")
    SmartPsiElementPointer<XmlTag> tagPointer = (SmartPsiElementPointer<XmlTag>)property.getOptionalValue1();
    if (tagPointer == null) {
      return null;
    }
    return tagPointer.getElement();
  }
}
