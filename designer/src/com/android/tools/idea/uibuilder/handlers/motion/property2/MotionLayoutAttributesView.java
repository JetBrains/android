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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.property.panel.api.FilteredPTableModel.PTableModelFactory;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.AddCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.AddMotionFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.DeleteCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.DeleteMotionFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.SubSectionControlAction;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider;
import com.android.tools.idea.uibuilder.property2.support.NeleTwoStateBooleanControlTypeProvider;
import com.android.tools.idea.uibuilder.property2.ui.EmptyTablePanel;
import com.android.tools.property.panel.api.EditorProvider;
import com.android.tools.property.panel.api.FilteredPTableModel;
import com.android.tools.property.panel.api.InspectorBuilder;
import com.android.tools.property.panel.api.InspectorLineModel;
import com.android.tools.property.panel.api.InspectorPanel;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.android.tools.property.panel.api.PropertiesView;
import com.android.tools.property.panel.api.TableLineModel;
import com.android.tools.property.panel.api.TableUIProvider;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import kotlin.jvm.functions.Function1;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesView} for motion layout property editor.
 */
public class MotionLayoutAttributesView extends PropertiesView<NelePropertyItem> {
  private static final String MOTION_VIEW_NAME = "Motion";
  private static final List<String> CONSTRAINT_SECTIONS = ImmutableList.of(
    MotionSceneAttrs.Tags.LAYOUT,
    MotionSceneAttrs.Tags.MOTION,
    MotionSceneAttrs.Tags.PROPERTY_SET,
    MotionSceneAttrs.Tags.TRANSFORM
  );

  public MotionLayoutAttributesView(@NotNull MotionLayoutAttributesModel model) {
    super(MOTION_VIEW_NAME, model);
    NeleEnumSupportProvider enumSupportProvider = new NeleEnumSupportProvider(model);
    NeleTwoStateBooleanControlTypeProvider controlTypeProvider = new NeleTwoStateBooleanControlTypeProvider(enumSupportProvider);
    EditorProvider<NelePropertyItem> editorProvider = EditorProvider.Companion.create(enumSupportProvider, controlTypeProvider);
    TableUIProvider tableUIProvider = TableUIProvider.Companion.create(NelePropertyItem.class, controlTypeProvider, editorProvider);
    getMain().getBuilders().add(new SelectedTargetBuilder());
    addTab("").getBuilders().add(new MotionInspectorBuilder(model, editorProvider, tableUIProvider));
  }

  private static class MotionInspectorBuilder implements InspectorBuilder<NelePropertyItem> {
    private final MotionLayoutAttributesModel myModel;
    private final EditorProvider<NelePropertyItem> myEditorProvider;
    private final TableUIProvider myTableUIProvider;
    private final XmlElementDescriptorProvider myDescriptorProvider;

    private MotionInspectorBuilder(@NotNull MotionLayoutAttributesModel model,
                                   @NotNull EditorProvider<NelePropertyItem> editorProvider,
                                   @NotNull TableUIProvider tableUIProvider) {
      myModel = model;
      myEditorProvider = editorProvider;
      myTableUIProvider = tableUIProvider;
      myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector,
                                  @NotNull PropertiesTable<NelePropertyItem> properties) {
      NelePropertyItem any = properties.getFirst();
      if (any == null) {
        return;
      }
      MotionSelection selection = (MotionSelection)any.getOptionalValue1();
      if (selection == null) {
        return;
      }

      switch (selection.getType()) {
        case CONSTRAINT:
          NelePropertyItem targetId = properties.getOrNull(ANDROID_URI, ATTR_ID);
          //boolean showDefaultValues = myModel.getProperties().getValues().stream().anyMatch(
          //  item -> !item.getNamespace().isEmpty() &&
          //          item != targetId &&
          //          (item.getRawValue() != null));

          addPropertyTable(inspector, selection, MotionSceneAttrs.Tags.CONSTRAINT, myModel, true, false, targetId);
          addSubTagSections(inspector, selection, myModel);
          break;

        case KEY_FRAME:
          NelePropertyItem target = properties.getOrNull(AUTO_URI, MotionSceneAttrs.Key.MOTION_TARGET);
          NelePropertyItem position = properties.getOrNull(AUTO_URI, MotionSceneAttrs.Key.FRAME_POSITION);
          if (target == null || position == null) {
            // All KeyFrames should have target and position.
            Logger.getInstance(NelePropertyItem.class).warn("KeyFrame without target and position");
            return;
          }
          inspector.addEditor(myEditorProvider.createEditor(position, false), null);
          addPropertyTable(inspector, selection, selection.getMotionSceneTagName(), myModel, false, false, target, position);
          break;

        default:
          addPropertyTable(inspector, selection, selection.getMotionSceneTagName(), myModel, false, false);
          break;
      }
      addCustomAttributes(inspector, selection, any, myModel);
    }

    private void addSubTagSections(@NotNull InspectorPanel inspector,
                                   @NotNull MotionSelection selection,
                                   @NotNull MotionLayoutAttributesModel model) {
      if (!shouldDisplaySection(MotionSceneAttrs.Tags.LAYOUT, selection)) {
        return;
      }
      for (String subTagName : CONSTRAINT_SECTIONS) {
        SubTagAttributesModel subModel = new SubTagAttributesModel(model, subTagName);
        addPropertyTable(inspector, selection, subTagName, subModel, true, true);
      }
    }

    private void addCustomAttributes(@NotNull InspectorPanel inspector,
                                     @NotNull MotionSelection selection,
                                     @NotNull NelePropertyItem any,
                                     @NotNull MotionLayoutAttributesModel model) {
      if (!shouldDisplaySection(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, selection)) {
        return;
      }
      SubTagAttributesModel customModel = new SubTagAttributesModel(model, MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
      Function1<NelePropertyItem, Boolean> filter = (item) -> item.getNamespace().isEmpty() && item.getRawValue() != null;

      FilteredPTableModel<NelePropertyItem> tableModel = PTableModelFactory.create(
        customModel, filter, PTableModelFactory.getAlphabeticalSortOrder(), Collections.emptyList(), false, true);
      AddCustomFieldAction addFieldAction = new AddCustomFieldAction(tableModel, any);
      DeleteCustomFieldAction deleteFieldAction = new DeleteCustomFieldAction();
      List<AnAction> actions = ImmutableList.<AnAction>builder().add(addFieldAction).add(deleteFieldAction).build();

      InspectorLineModel title = inspector.addExpandableTitle("CustomAttributes", true, actions);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, title);
      inspector.addComponent(new EmptyTablePanel(addFieldAction, lineModel), title);
      deleteFieldAction.setLineModel(lineModel);
    }

    private void addPropertyTable(@NotNull InspectorPanel inspector,
                                  @NotNull MotionSelection selection,
                                  @Nullable String sectionTagName,
                                  @NotNull PropertiesModel<NelePropertyItem> model,
                                  boolean showDefaultValues,
                                  boolean showSectionControl,
                                  @NotNull NelePropertyItem... excluded) {
      if (!shouldDisplaySection(sectionTagName, selection)) {
        return;
      }
      NelePropertyItem any = model.getProperties().getFirst();
      Function1<NelePropertyItem, Boolean> filter =
        (item) -> !item.getNamespace().isEmpty() &&
                  ArrayUtil.find(excluded, item) < 0 &&
                  (item.getRawValue() != null || (showDefaultValues && item.getDefaultValue() != null));

      FilteredPTableModel<NelePropertyItem> tableModel =
        PTableModelFactory.create(
          model, filter, PTableModelFactory.getAlphabeticalSortOrder(), Collections.emptyList(), true, true);
      SubSectionControlAction controlAction = new SubSectionControlAction(any);
      AddMotionFieldAction addFieldAction = new AddMotionFieldAction(myModel, tableModel, model.getProperties());
      DeleteMotionFieldAction deleteFieldAction = new DeleteMotionFieldAction(tableModel);
      ImmutableList.Builder<AnAction> actionsBuilder = ImmutableList.builder();
      if (showSectionControl) {
        actionsBuilder.add(controlAction);
      }
      actionsBuilder.add(addFieldAction);
      actionsBuilder.add(deleteFieldAction);
      List<AnAction> actions = actionsBuilder.build();

      InspectorLineModel title = inspector.addExpandableTitle(sectionTagName, true, actions);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, title);
      inspector.addComponent(new EmptyTablePanel(addFieldAction, lineModel), title);
      controlAction.setLineModel(title);
      addFieldAction.setLineModel(lineModel);
      deleteFieldAction.setLineModel(lineModel);
    }

    private boolean shouldDisplaySection(@Nullable String section, @NotNull MotionSelection selection) {
      if (section == null) {
        return false;
      }
      MotionSceneTag tag = selection.getMotionSceneTag();
      switch (section) {
        case MotionSceneAttrs.Tags.CONSTRAINT:
          if (selection.getType() != MotionEditorSelector.Type.CONSTRAINT) {
            return false;
          }
          if (tag == null) {
            // Non existent constraint tag.
            // Show the inherited constraint attributes.
            return true;
          }
          // If this is not a sectioned constraint then display the tag.
          // If this it is a sectioned constraint but it has significant attributes then display the tag as well.
          Set<String> attrs = tag.getAttrList().keySet();
          return !isSectionedConstraint(tag) || attrs.size() > 1 || !attrs.contains(ATTR_ID);

        case MotionSceneAttrs.Tags.LAYOUT:
        case MotionSceneAttrs.Tags.PROPERTY_SET:
        case MotionSceneAttrs.Tags.TRANSFORM:
        case MotionSceneAttrs.Tags.MOTION:
          if (selection.getType() != MotionEditorSelector.Type.CONSTRAINT) {
            return false;
          }
          return tag != null && isSectionedConstraint(tag);

        case MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE:
          if (tag == null && selection.getType() == MotionEditorSelector.Type.CONSTRAINT) {
            return true;
          }
          return tag != null && canHaveCustomAttributes(tag);

        default:
          return section.equals(selection.getMotionSceneTagName());
      }
    }

    private static boolean isSectionedConstraint(@NotNull MotionSceneTag constraintTag) {
      for (MTag tag : constraintTag.getChildTags()) {
        if (CONSTRAINT_SECTIONS.contains(tag.getTagName())) {
          return true;
        }
      }
      return false;
    }

    private boolean canHaveCustomAttributes(@NotNull MotionSceneTag tag) {
      XmlTag xml = tag.getXmlTag();
      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(xml);
      if (elementDescriptor == null) {
        return false;
      }
      for (XmlElementDescriptor childDescriptor : elementDescriptor.getElementsDescriptors(xml)) {
        if (childDescriptor.getDefaultName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void resetCache() {
    }
  }
}
