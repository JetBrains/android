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

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutPropertyProvider.mapToCustomType;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTagWriter;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.google.common.base.Predicates;
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
import java.util.function.Predicate;
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
    setDefaultValueProvider(new MotionDefaultPropertyValueProvider());
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
    String section = getConstraintSection(property);
    if (motionTag == null) {
      return null;
    }
    if (motionTag.getTagName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      return motionTag.getAttributeValue(mapToCustomType(property.getType()));
    }
    if (section == null) {
      return motionTag.getAttributeValue(property.getName());
    }
    MotionSceneTag sectionTag = getConstraintSectionTag(motionTag, section);
    if (sectionTag == null ){
      return null;
    }
    return sectionTag.getAttributeValue(property.getName());
  }

  @Override
  public void setPropertyValue(@NotNull NelePropertyItem property, @Nullable String newValue) {
    MotionSceneTag motionTag = getMotionTag(property);
    String section = getConstraintSection(property);
    if (motionTag == null) {
      return;
    }
    if (motionTag.getTagName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      writeCustomTagAttribute(motionTag, property, newValue);
    }
    else if (section == null) {
      writeTagAttribute(motionTag, property, newValue);
    }
    else {
      MotionSceneTag sectionTag = getConstraintSectionTag(motionTag, section);
      if (sectionTag != null ){
        writeTagAttribute(sectionTag, property, newValue);
      }
      else if (newValue != null) {
        createConstraintTag(motionTag, section, property, newValue);
      }
    }
  }

  private static void writeTagAttribute(@NotNull MotionSceneTag tag, @NotNull NelePropertyItem property, @Nullable String newValue) {
    MotionSceneTagWriter writer = tag.getTagWriter();
    writer.setAttribute(property.getNamespace(), property.getName(), newValue);
    writer.commit(String.format("Set %1$s.%2$s to %3$s", tag.getTagName(), property.getName(), String.valueOf(newValue)));
  }

  private static void writeCustomTagAttribute(@NotNull MotionSceneTag tag, @NotNull NelePropertyItem property, @Nullable String newValue) {
    MotionSceneTagWriter writer = tag.getTagWriter();
    writer.setAttribute(property.getNamespace(), mapToCustomType(property.getType()), newValue);
    writer.commit(String.format("Set %1$s.%2$s to %3$s", tag.getTagName(), property.getName(), String.valueOf(newValue)));
  }

  private static void createConstraintTag(@NotNull MotionSceneTag constraintTag,
                                          @NotNull String section,
                                          @NotNull NelePropertyItem property,
                                          @Nullable String newValue) {
    MTag.TagWriter writer = constraintTag.getChildTagWriter(section);
    MotionAttributes attrs = MotionDefaultPropertyValueProvider.getMotionAttributesForTag(property);
    if (attrs != null) {
      Predicate<MotionAttributes.DefinedAttribute> isApplicable = findIncludePredicate(section);
      for (MotionAttributes.DefinedAttribute attr : attrs.getAttrMap().values()) {
        if (isApplicable.test(attr)) {
          writer.setAttribute(attr.getNamespace(), attr.getName(), attr.getValue());
        }
      }
    }
    writer.setAttribute(property.getNamespace(), property.getName(), newValue);
    writer.commit(String.format("Create %1$s tag", section));
  }

  private static Predicate<MotionAttributes.DefinedAttribute> findIncludePredicate(@NotNull String sectionName) {
    switch (sectionName) {
      case MotionSceneAttrs.Tags.LAYOUT:
        return attr -> attr.isLayoutAttribute();
      case MotionSceneAttrs.Tags.PROPERTY_SET:
        return attr -> attr.isPropertySetAttribute();
      case MotionSceneAttrs.Tags.TRANSFORM:
        return attr -> attr.isTransformAttribute();
      case MotionSceneAttrs.Tags.MOTION:
        return attr -> attr.isMotionAttribute();
      default:
        return Predicates.alwaysFalse();
    }
  }

  public void createCustomXmlTag(@NotNull MotionSceneTag keyFrameOrConstraint,
                                 @NotNull String attrName,
                                 @NotNull String value,
                                 @NotNull MotionSceneModel.CustomAttributes.Type type,
                                 @NotNull Consumer<MotionSceneTag> operation) {
    List<MTag> oldTags = Arrays.stream(keyFrameOrConstraint.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE))
      .filter(tag -> attrName.equals(tag.getAttributeValue(MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME)))
      .collect(Collectors.toList());

    String newValue = StringUtil.isNotEmpty(value) ? value : type.getDefaultValue();
    String commandName = String.format("Set %1$s.%2$s to %3$s", MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, attrName, newValue);

    Runnable transaction = () -> {
      oldTags.forEach(tag -> ((MotionSceneTag)tag).getXmlTag().delete());

      MTag.TagWriter writer = keyFrameOrConstraint.getChildTagWriter(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
      writer.setAttribute(AUTO_URI, MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME, attrName);
      writer.setAttribute(AUTO_URI, type.getTagName(), newValue);
      MTag createdMotionTag = writer.commit(commandName);
      operation.accept((MotionSceneTag)createdMotionTag);
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        commandName,
        null,
        transaction,
        keyFrameOrConstraint.getXmlTag().getContainingFile()));
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
        "Delete " + tag.getLocalName(),
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
  public static String getConstraintSection(@NotNull NelePropertyItem property) {
    return (String)property.getOptionalValue2();
  }

  @Nullable
  public static MotionSceneTag getConstraintSectionTag(@NotNull MotionSceneTag constraint, @NotNull String sectionTagName) {
    MTag[] tags = constraint.getChildTags(sectionTagName);
    if (tags.length == 0 || !(tags[0] instanceof MotionSceneTag)) {
      return null;
    }
    // TODO: If there are multiple sub tags (by mistake) should we write to all of them?
    return (MotionSceneTag)tags[0];
  }

  @Nullable
  public static XmlTag getTag(@NotNull NelePropertyItem property) {
    MotionSceneTag motionTag = (MotionSceneTag)property.getOptionalValue1();
    if (motionTag == null) {
      return null;
    }
    return motionTag.getXmlTag();
  }
}
