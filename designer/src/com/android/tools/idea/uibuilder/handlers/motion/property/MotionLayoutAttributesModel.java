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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutPropertyProvider.mapToCustomType;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.property.NlPropertiesModel;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.android.tools.property.panel.api.TableLineModel;
import com.google.common.base.Predicates;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import kotlin.jvm.functions.Function0;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel extends NlPropertiesModel {
  private final MotionLayoutPropertyProvider myMotionLayoutPropertyProvider;
  private Map<String, PropertiesTable<NlPropertyItem>> myAllProperties;

  public MotionLayoutAttributesModel(
    @NotNull Disposable parentDisposable,
    @NotNull AndroidFacet facet,
    @NotNull MergingUpdateQueue updateQueue
  ) {
    super(parentDisposable, new MotionLayoutPropertyProvider(facet), facet, updateQueue, false);
    myMotionLayoutPropertyProvider = (MotionLayoutPropertyProvider)getProvider();
    setDefaultValueProvider(new MotionDefaultPropertyValueProvider());
  }

  public Map<String, PropertiesTable<NlPropertyItem>> getAllProperties() {
    return myAllProperties;
  }

  @Override
  protected void loadProperties(
    @Nullable Object accessoryType,
    @Nullable Object accessory,
    @NotNull List<? extends NlComponent> components,
    @NotNull Function0<Boolean> wantUpdate
  ) {
    if (accessoryType == null || accessory == null || !wantUpdate.invoke()) {
      return;
    }

    MotionEditorSelector.Type type = (MotionEditorSelector.Type)accessoryType;
    MTag[] tags = (MTag[])accessory;
    MotionSelection selection = new MotionSelection(type, tags, components);
    MotionSelection sameOldSelection = findSameOldSelection(selection);
    if (sameOldSelection != null) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (wantUpdate.invoke()) {
          sameOldSelection.update(selection);
          firePropertyValueChanged();
        }
      });
      return;
    }
    setLastUpdateCompleted(false);
    Callable<Map<String, PropertiesTable<NlPropertyItem>>> getProperties = () -> {
      Map<String, PropertiesTable<NlPropertyItem>> newProperties =
        myMotionLayoutPropertyProvider.getAllProperties(MotionLayoutAttributesModel.this, selection);
      return newProperties;
    };
    Consumer<Map<String, PropertiesTable<NlPropertyItem>>> notifyUI = newProperties -> {
      try {
        if (wantUpdate.invoke()) {
          updateLiveListeners(components);
          PropertiesTable<NlPropertyItem> first = newProperties.isEmpty() ? PropertiesTable.Companion.emptyTable()
                                                                          : newProperties.values().iterator().next();
          myAllProperties = newProperties;
          setProperties(first);
          firePropertiesGenerated();
        }
      }
      finally {
        setUpdateCount(getUpdateCount() + 1);
        setLastUpdateCompleted(true);
      }
    };

    ReadAction
      .nonBlocking(getProperties)
      .inSmartMode(getProject())
      .expireWith(this)
      .finishOnUiThread(ModalityState.defaultModalityState(), notifyUI)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private MotionSelection findSameOldSelection(@NotNull MotionSelection selection) {
    if (myAllProperties == null) {
      return null;
    }
    PropertiesTable<NlPropertyItem> nonEmptyTable = myAllProperties.values().stream()
      .filter(table -> !table.isEmpty())
      .findFirst()
      .orElse(null);

    if (nonEmptyTable == null) {
      return null;
    }
    NlPropertyItem property = nonEmptyTable.getFirst();
    if (property == null) {
      Logger.getInstance(MotionLayoutAttributesModel.class).info("This table should not be empty!");
      // Should never happen...
      return null;
    }
    MotionSelection oldSelection = getMotionSelection(property);
    return oldSelection != null && oldSelection.sameSelection(selection) ? oldSelection : null;
  }

  @Override
  @Nullable
  public String getPropertyValue(@NotNull NlPropertyItem property) {
    MotionSelection selection = getMotionSelection(property);
    String subTag = getSubTag(property);
    if (selection == null) {
      return null;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      if (property.getNamespace().equals(ANDROID_URI) && property.getName().equals(ATTR_ID)) {
        return selection.getComponentId();
      }
      // The rest of these attributes are given as default values...
      return null;
    }
    if (subTag != null && subTag.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      MTag customTag = findCustomTag(motionTag, property.getName());
      return customTag != null ? customTag.getAttributeValue(mapToCustomType(property.getType())) : null;
    }
    if (subTag == null) {
      return motionTag.getAttributeValue(property.getName());
    }
    MotionSceneTag sectionTag = getSubTag(motionTag, subTag);
    if (sectionTag == null) {
      return null;
    }
    return sectionTag.getAttributeValue(property.getName());
  }

  @Override
  public void deactivate() {
    super.deactivate();
    myAllProperties = Collections.emptyMap();
  }

  @Override
  @Nullable
  public XmlTag getPropertyTag(@NotNull NlPropertyItem property) {
    MotionSelection selection = getMotionSelection(property);
    if (selection == null) {
      return null;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      return null;
    }
    return selection.getXmlTag(motionTag);
  }

  @Override
  public void setPropertyValue(@NotNull NlPropertyItem property, @Nullable String newValue) {
    String attributeName = property.getName();
    MotionSelection selection = getMotionSelection(property);
    String subTagName = getSubTag(property);
    MTag.TagWriter tagWriter = null;
    if (selection == null) {
      return;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      tagWriter = createConstraintTag(selection);
    }
    else if (subTagName != null && subTagName.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      MTag customTag = findCustomTag(motionTag, property.getName());
      if (customTag != null) {
        tagWriter = MotionSceneUtils.getTagWriter(customTag);
        attributeName = mapToCustomType(property.getType());
      }
    }
    else if (subTagName == null) {
      tagWriter = MotionSceneUtils.getTagWriter(motionTag);
    }
    else if (selection.getType() == MotionEditorSelector.Type.CONSTRAINT || selection.getType() == MotionEditorSelector.Type.TRANSITION) {
      MotionSceneTag sectionTag = getSubTag(motionTag, subTagName);
      if (sectionTag != null) {
        tagWriter = MotionSceneUtils.getTagWriter(sectionTag);
      }
      else if (newValue != null) {
        tagWriter = createSubTag(selection, motionTag, subTagName);
      }
    }
    if (tagWriter != null) {
      tagWriter.setAttribute(property.getNamespace(), attributeName, newValue);
      tagWriter.commit(String.format("Set %1$s.%2$s to %3$s", tagWriter.getTagName(), property.getName(), newValue));
    }
  }

  @Override
  public void browseToValue(@NotNull NlPropertyItem property) {
    Navigation.INSTANCE.browseToValue(property);
  }

  public static MTag.TagWriter createSubTag(@NotNull MotionSelection selection,
                                            @NotNull MotionSceneTag constraintTag,
                                            @NotNull String section) {
    MTag.TagWriter tagWriter = MotionSceneUtils.getChildTagWriter(constraintTag, section);
    MotionAttributes attrs = selection.getMotionAttributes();
    if (attrs != null) {
      Predicate<MotionAttributes.DefinedAttribute> isApplicable = findIncludePredicate(section);
      for (MotionAttributes.DefinedAttribute attr : attrs.getAttrMap().values()) {
        if (isApplicable.test(attr)) {
          tagWriter.setAttribute(attr.getNamespace(), attr.getName(), attr.getValue());
        }
      }
    }
    return tagWriter;
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

  public void addCustomProperty(
    @NotNull String attributeName,
    @NotNull String value,
    @NotNull CustomAttributeType type,
    @NotNull MotionSelection selection,
    @NotNull TableLineModel lineModel
  ) {
    Consumer<MotionSceneTag> applyToModel = newCustomTag -> {
      NlPropertyItem newProperty = MotionLayoutPropertyProvider.createCustomProperty(
        attributeName, type.getTagName(), selection, this);
      lineModel.addItem(newProperty);

      // Add to the property model since the model may treat this as a property update (not a new selection).
      PropertiesTable<NlPropertyItem> customProperties = getAllProperties().get(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
      if (customProperties == null) {
        Table<String, String, NlPropertyItem> customTable = HashBasedTable.create(3, 10);
        customProperties = PropertiesTable.Companion.create(customTable);
        getAllProperties().put(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, customProperties);
      }
      customProperties.put(newProperty);
    };

    createCustomXmlTag(selection, attributeName, value, type, applyToModel);
  }

  /**
   * Given the current selection create a new custom tag with the specified attrName, value, and type
   *
   * Upon completion perform the specified operation with the created custom tag.
   * Note that this method may create the constraint tag for the custom tag as well.
   */
  public void createCustomXmlTag(@NotNull MotionSelection selection,
                                 @NotNull String attrName,
                                 @NotNull String value,
                                 @NotNull CustomAttributeType type,
                                 @NotNull Consumer<MotionSceneTag> operation) {
    String valueAttrName = type.getTagName();
    String newValue = StringUtil.isNotEmpty(value) ? value : type.getDefaultValue();
    String commandName = String.format("Set %1$s.%2$s to %3$s", MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, attrName, newValue);
    MTag.TagWriter tagWriter = null;
    MTag oldCustomTag = null;

    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      tagWriter = createConstraintTag(selection);
      if (tagWriter == null) {
        // Should not happen!
        return;
      }
    }
    else {
      oldCustomTag = findCustomTag(motionTag, attrName);
    }
    MTag.TagWriter constraintWriter = tagWriter;

    if (tagWriter != null) {
      tagWriter = tagWriter.getChildTagWriter(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
    }
    else if (oldCustomTag != null) {
      tagWriter = oldCustomTag.getTagWriter();
    }
    else {
      tagWriter = motionTag.getChildTagWriter(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
    }
    tagWriter.setAttribute(AUTO_URI, MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME, attrName);
    tagWriter.setAttribute(AUTO_URI, valueAttrName, newValue);
    if (oldCustomTag != null) {
      for (String attr : MotionSceneAttrs.ourCustomAttribute) {
        if (!attr.equals(valueAttrName)) {
          tagWriter.setAttribute(AUTO_URI, attr, null);
        }
      }
    }
    MTag.TagWriter committer = constraintWriter != null ? constraintWriter : tagWriter;

    Runnable transaction = () -> {
      MTag createdCustomTag = committer.commit(commandName);
      if (!createdCustomTag.getTagName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
        createdCustomTag = createdCustomTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)[0];
      }
      operation.accept((MotionSceneTag)createdCustomTag);
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    WriteCommandAction.runWriteCommandAction(
      getFacet().getModule().getProject(),
      commandName,
      null,
      transaction,
      selection.getSceneFile());
  }

  /**
   * Create a <Constraint> tag and copy over the inherited attributes.
   * @return an uncommitted TagWriter on the created tag.
   */
  private static MTag.TagWriter createConstraintTag(@NotNull MotionSelection selection) {
    if (selection.getType() != MotionEditorSelector.Type.CONSTRAINT) {
      // Should not happen!
      return null;
    }
    MotionAttributes attrs = selection.getMotionAttributes();
    if (attrs == null) {
      // Should not happen!
      return null;
    }
    MTag constraintSetTag = attrs.getConstraintSet();
    if (constraintSetTag == null) {
      // Should not happen!
      return null;
    }
    MTag.TagWriter tagWriter = constraintSetTag.getChildTagWriter(MotionSceneAttrs.Tags.CONSTRAINT);
    if (tagWriter == null) {
      // Should not happen!
      return null;
    }
    tagWriter.setAttribute(ANDROID_URI, ATTR_ID, selection.getComponentId());
    for (MotionAttributes.DefinedAttribute attr : attrs.getAttrMap().values()) {
      tagWriter.setAttribute(attr.getNamespace(), attr.getName(), attr.getValue());
    }
    return tagWriter;
  }

  @Override
  protected boolean wantSelectionUpdate(
    @Nullable DesignSurface<?> surface,
    @Nullable DesignSurface<?> activeSurface,
    @Nullable AccessoryPanelInterface panel,
    @Nullable AccessoryPanelInterface activePanel,
    @Nullable Object selectedAccessoryType,
    @Nullable Object selectedAccessory
  ) {
    return surface != null &&
           surface == activeSurface &&
           panel instanceof MotionDesignSurfaceEdits &&
           panel == activePanel &&
           selectedAccessoryType != null &&
           selectedAccessory != null;
  }

  @Nullable
  public static MotionSelection getMotionSelection(@NotNull NlPropertyItem property) {
    return (MotionSelection)property.getOptionalValue1();
  }

  @Nullable
  public static String getSubTag(@NotNull NlPropertyItem property) {
    return (String)property.getOptionalValue2();
  }

  @Nullable
  public static MTag findCustomTag(MotionSceneTag motionTag, @Nullable String attrName) {
    if (attrName == null) {
      return null;
    }
    return Arrays.stream(motionTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE))
      .filter(child -> attrName.equals(child.getAttributeValue(MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME)))
      .findFirst()
      .orElse(null);
  }


  @Nullable
  public static MotionSceneTag getSubTag(@NotNull MotionSceneTag constraint, @NotNull String sectionTagName) {
    MTag[] tags = constraint.getChildTags(sectionTagName);
    if (tags.length == 0 || !(tags[0] instanceof MotionSceneTag)) {
      return null;
    }
    // TODO: If there are multiple sub tags (by mistake) should we write to all of them?
    return (MotionSceneTag)tags[0];
  }
}
