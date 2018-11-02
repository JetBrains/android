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

import com.android.tools.idea.common.property2.api.PropertiesModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel extends NelePropertiesModel {

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable, @NotNull AndroidFacet facet) {
    super(parentDisposable, new MotionLayoutPropertyProvider(facet), facet);
  }

  @Override
  @Nullable
  public String getPropertyValue(@NotNull NelePropertyItem property) {
    XmlTag tag = getTag(property);
    if (tag == null) {
      return null;
    }
    return tag.getAttributeValue(property.getName(), property.getNamespace());
  }

  @Override
  public void setPropertyValue(@NotNull NelePropertyItem property, @Nullable String newValue) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () -> {
      XmlTag tag = getTag(property);
      if (tag != null) {
        WriteCommandAction.runWriteCommandAction(
          getFacet().getModule().getProject(),
          "Set $componentName.$name to $newValue",
          null,
          () -> tag.setAttribute(property.getName(), property.getNamespace(), newValue));
      }
    });
  }

  @Override
  protected void updateDesignSurface(@Nullable DesignSurface oldSurface, @Nullable DesignSurface newSurface) {
    setAccessoryPanelListener(oldSurface, newSurface);
    useCurrentPanel(newSurface);
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
    SmartPsiElementPointer<XmlTag> tagPointer = (SmartPsiElementPointer<XmlTag>)property.getOptionalValue();
    if (tagPointer == null) {
      return null;
    }
    return tagPointer.getElement();
  }
}
