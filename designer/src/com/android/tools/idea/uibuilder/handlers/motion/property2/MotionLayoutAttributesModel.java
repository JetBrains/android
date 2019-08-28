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
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import kotlin.jvm.functions.Function0;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel extends NelePropertiesModel {
  private final MotionLayoutPropertyProvider myMotionLayoutPropertyProvider;
  private Map<String, PropertiesTable<NelePropertyItem>> myAllProperties;

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable, @NotNull AndroidFacet facet) {
    super(parentDisposable, new MotionLayoutPropertyProvider(facet), facet, false);
    myMotionLayoutPropertyProvider = (MotionLayoutPropertyProvider)getProvider();
  }

  public Map<String, PropertiesTable<NelePropertyItem>> getAllProperties() {
    return myAllProperties;
  }

  @Override
  protected boolean loadProperties(@Nullable Object accessory,
                                   @NotNull List<? extends NlComponent> components,
                                   @NotNull Function0<Boolean> wantUpdate) {
    if (accessory == null || !wantUpdate.invoke()) {
      return false;
    }

    MotionSceneTag tag = (MotionSceneTag)accessory;
    Map<String, PropertiesTable<NelePropertyItem>> newProperties =
      myMotionLayoutPropertyProvider.getAllProperties(this, tag, components);
    setLastUpdateCompleted(false);

    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        if (wantUpdate.invoke()) {
          updateLiveListeners(components);
          PropertiesTable<NelePropertyItem> first = newProperties.isEmpty() ? PropertiesTable.Companion.emptyTable()
                                                                            : newProperties.values().iterator().next();
          myAllProperties = newProperties;
          setProperties(first);
          firePropertiesGenerated();
        }
      }
      finally {
        setLastUpdateCompleted(true);
      }
    });
    return true;
  }

  @Override
  @Nullable
  public String getPropertyValue(@NotNull NelePropertyItem property) {
    MotionSceneTag motionTag = getMotionTag(property);
    if (motionTag == null) {
      return null;
    }
    XmlTag tag = motionTag.getXmlTag();
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
    XmlTag subTag = getSubTag(property, tag);
    if (subTag != null) {
      tag = subTag;
    }
    if (tag.getLocalName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
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
        XmlTag subTag = getSubTag(property, tag);
        XmlTag tagToUpdate = subTag != null ? subTag : tag;
        WriteCommandAction.runWriteCommandAction(
          getFacet().getModule().getProject(),
          "Set $componentName.$name to $newValue",
          null,
          () -> setPropertyValue(tagToUpdate, component, property, newValue));
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
    if (tag.getLocalName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      tag.setAttribute(mapToCustomType(property.getType()), SdkConstants.AUTO_URI, newValue);
    }
    else {
      tag.setAttribute(property.getName(), property.getNamespace(), newValue);
    }
    MotionSceneModel.saveAndNotify(tag.getContainingFile(), property.getComponents().get(0).getModel());
  }

  public void createCustomXmlTag(@NotNull MotionSceneTag keyFrameOrConstraint,
                                 @NotNull String attrName,
                                 @NotNull String value,
                                 @NotNull MotionSceneModel.CustomAttributes.Type type,
                                 @NotNull Consumer<MotionSceneTag> operation) {
    XmlTag parentTag = keyFrameOrConstraint.getXmlTag();
    if (parentTag == null) {
      return;
    }
    List<XmlTag> oldTags = Arrays.stream(parentTag.findSubTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE))
      .filter(tag -> attrName.equals(tag.getAttribute(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, SdkConstants.AUTO_URI)))
      .collect(Collectors.toList());

    Runnable transaction = () -> {
      oldTags.forEach(tag -> tag.delete());

      XmlTag createdTag = parentTag.createChildTag(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, null, null, false);
      createdTag = parentTag.addSubTag(createdTag, false);
      createdTag.setAttribute(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, SdkConstants.AUTO_URI, attrName);
      createdTag.setAttribute(type.getTagName(), SdkConstants.AUTO_URI, StringUtil.isNotEmpty(value) ? value : type.getDefaultValue());
      MotionSceneTag createdMotionTag = new MotionSceneTag(createdTag, keyFrameOrConstraint);
      operation.accept(createdMotionTag);
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        "Set $componentName.$name to $newValue",
        null,
        transaction,
        parentTag.getContainingFile()));
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
    return panel == activePanel && panel.getSelectedAccessory() != null && panel instanceof MotionDesignSurfaceEdits;
  }

  @Nullable
  public static MotionSceneTag getMotionTag(@NotNull NelePropertyItem property) {
    return (MotionSceneTag)property.getOptionalValue1();
  }

  @Nullable
  public static XmlTag getTag(@NotNull NelePropertyItem property) {
    MotionSceneTag motionTag = (MotionSceneTag)property.getOptionalValue1();
    if (motionTag == null) {
      return null;
    }
    return motionTag.getXmlTag();
  }

  @Nullable
  public static XmlTag getSubTag(@NotNull NelePropertyItem property, @NotNull XmlTag tag) {
    String subTagName = (String)property.getOptionalValue2();
    if (subTagName == null) {
      return null;
    }
    for (XmlTag subTag : tag.getSubTags()) {
      if (subTag.getLocalName().equals(subTagName)) {
        return subTag;
      }
    }
    return null;
  }
}
