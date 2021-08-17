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

import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.property.panel.api.FilteredPTableModel.PTableModelFactory;

import com.android.SdkConstants;
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.handlers.constraint.MotionConstraintPanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.property.action.AddCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property.action.AddMotionFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property.action.DeleteCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property.action.DeleteMotionFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property.action.SubSectionControlAction;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.inspector.InspectorSection;
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider;
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider;
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel;
import com.android.tools.idea.uibuilder.property.ui.TransformsPanel;
import com.android.tools.idea.uibuilder.property.ui.EasingCurvePanel;
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
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.XmlElementDescriptor;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesView} for motion layout property editor.
 */
public class MotionLayoutAttributesView extends PropertiesView<NlPropertyItem> {
  private static final String MOTION_VIEW_NAME = "Motion";
  private static final List<String> CONSTRAINT_SECTIONS = ImmutableList.of(
    MotionSceneAttrs.Tags.LAYOUT,
    MotionSceneAttrs.Tags.MOTION,
    MotionSceneAttrs.Tags.PROPERTY_SET,
    MotionSceneAttrs.Tags.TRANSFORM
  );

  public MotionLayoutAttributesView(@NotNull MotionLayoutAttributesModel model) {
    super(MOTION_VIEW_NAME, model);
    NlEnumSupportProvider enumSupportProvider = new NlEnumSupportProvider(model);
    NlTwoStateBooleanControlTypeProvider controlTypeProvider = new NlTwoStateBooleanControlTypeProvider(enumSupportProvider);
    EditorProvider<NlPropertyItem> editorProvider = EditorProvider.Companion.create(enumSupportProvider, controlTypeProvider);
    TableUIProvider tableUIProvider = TableUIProvider.Companion.create(NlPropertyItem.class, controlTypeProvider, editorProvider);
    getMain().getBuilders().add(new SelectedTargetBuilder());
    addTab("").getBuilders().add(new MotionInspectorBuilder(model, tableUIProvider, enumSupportProvider));
  }

  private static class MotionInspectorBuilder implements InspectorBuilder<NlPropertyItem> {
    private final MotionLayoutAttributesModel myModel;
    private final TableUIProvider myTableUIProvider;
    private final XmlElementDescriptorProvider myDescriptorProvider;
    private final NlEnumSupportProvider myEnumSupportProvider;

    private MotionInspectorBuilder(@NotNull MotionLayoutAttributesModel model,
                                   @NotNull TableUIProvider tableUIProvider,
                                   @NotNull NlEnumSupportProvider enumSupportProvider) {
      myModel = model;
      myTableUIProvider = tableUIProvider;
      myDescriptorProvider = new AndroidDomElementDescriptorProvider();
      myEnumSupportProvider = enumSupportProvider;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector,
                                  @NotNull PropertiesTable<NlPropertyItem> properties) {
      NlPropertyItem any = properties.getFirst();
      if (any == null) {
        return;
      }
      MotionSelection selection = (MotionSelection)any.getOptionalValue1();
      if (selection == null) {
        return;
      }

      switch (selection.getType()) {
        case CONSTRAINT:
          boolean showConstraintPanel = !shouldDisplaySection(MotionSceneAttrs.Tags.LAYOUT, selection);
          addPropertyTable(inspector, selection, MotionSceneAttrs.Tags.CONSTRAINT, myModel, true, false, showConstraintPanel);
          if (StudioFlags.NELE_TRANSFORM_PANEL.get()) {
            addTransforms(inspector, selection, myModel, properties);
          }
          if (StudioFlags.NELE_TRANSITION_PANEL.get()) {
            ArrayList<String> attributes = new ArrayList<>();
            attributes.add("transitionEasing");
            attributes.add("pathMotionArc");
            attributes.add("transitionPathRotate");
            addTransition(inspector, InspectorSection.TRANSITION, selection, myModel, "transitionEasing", properties, attributes);
          }
          addSubTagSections(inspector, selection, myModel);
          break;

        case TRANSITION:
          addPropertyTable(inspector, selection, selection.getMotionSceneTagName(), myModel, false, false, false);
          if (StudioFlags.NELE_TRANSITION_PANEL.get()) {
            ArrayList<String> attributes = new ArrayList<>();
            attributes.add("motionInterpolator");
            attributes.add("staggered");
            attributes.add("autoTransition");
            attributes.add("pathMotionArc");
            attributes.add("layoutDuringTransition");
            addTransition(inspector, InspectorSection.TRANSITION_MODIFIERS, selection, myModel, "motionInterpolator", properties, attributes);
          }
          addSubTagSections(inspector, selection, myModel);
          break;

        default:
          addPropertyTable(inspector, selection, selection.getMotionSceneTagName(), myModel, false, false, false);
          if (StudioFlags.NELE_TRANSFORM_PANEL.get()) {
            Map<String, PropertiesTable<NlPropertyItem>> allProperties = myModel.getAllProperties();
            if (allProperties.containsKey("KeyAttribute")) {
              addTransforms(inspector, selection, myModel, properties);
            }
          }
          break;
      }
      boolean showDefaultValues = selection.getType() == MotionEditorSelector.Type.CONSTRAINT;
      addCustomAttributes(inspector, selection, myModel, showDefaultValues);
    }

    private void addSubtitle(InspectorPanel inspector, String s, InspectorLineModel titleLine) {
      JComponent component = new JLabel(s);
      component.setBorder(new EmptyBorder(8, 8, 8, 8));
      inspector.addComponent(component, titleLine);
    }

    private class MySeparator extends AdtSecondaryPanel {
      MySeparator() {
        super(new BorderLayout());
        add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER);
      }
      @Override
      public void updateUI() {
        super.updateUI();
        setBorder(JBUI.Borders.empty(4));
      }
    }

    private void addTransforms(@NotNull InspectorPanel inspector,
                               @NotNull MotionSelection selection,
                               @NotNull MotionLayoutAttributesModel model,
                               @NotNull PropertiesTable<NlPropertyItem> properties) {
      InspectorLineModel titleModel = inspector.addExpandableTitle(InspectorSection.TRANSFORMS.getTitle(), false, Collections.emptyList());
      inspector.addComponent(new TransformsPanel(model, properties), titleModel);

      ArrayList<String> rotationAttributes = new ArrayList<>();
      rotationAttributes.add("rotationX");
      rotationAttributes.add("rotationY");
      rotationAttributes.add("rotation");

      ArrayList<String> attributes = new ArrayList<>();
      attributes.add("scaleX");
      attributes.add("scaleY");
      attributes.add("translationX");
      attributes.add("translationY");
      attributes.add("alpha");
      attributes.add("visibility");

      NlTwoStateBooleanControlTypeProvider controlTypeProvider = new NlTwoStateBooleanControlTypeProvider(myEnumSupportProvider);
      EditorProvider<NlPropertyItem> editorProvider = EditorProvider.Companion.create(myEnumSupportProvider, controlTypeProvider);

      for (String attributeName : rotationAttributes) {
        NlPropertyItem property = properties.getOrNull(SdkConstants.ANDROID_URI, attributeName);
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel);
        }
      }
      inspector.addComponent(new MySeparator(), titleModel);
      addSubtitle(inspector, "Other Transforms", titleModel);

      for (String attributeName : attributes) {
        NlPropertyItem property = properties.getOrNull(SdkConstants.ANDROID_URI, attributeName);
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel);
        }
      }
    }

    private void addTransition(@NotNull InspectorPanel inspector,
                               @NotNull InspectorSection title,
                               @NotNull MotionSelection selection,
                               @NotNull MotionLayoutAttributesModel model,
                               @NotNull String easingAttributeName,
                               @NotNull PropertiesTable<NlPropertyItem> properties,
                               @NotNull ArrayList<String> attributes) {
      InspectorLineModel titleModel = inspector.addExpandableTitle(title.getTitle(), false, Collections.emptyList());
      inspector.addComponent(new EasingCurvePanel(model, easingAttributeName, properties), titleModel);

      NlTwoStateBooleanControlTypeProvider controlTypeProvider = new NlTwoStateBooleanControlTypeProvider(myEnumSupportProvider);
      EditorProvider<NlPropertyItem> editorProvider = EditorProvider.Companion.create(myEnumSupportProvider, controlTypeProvider);

      for (String attributeName : attributes) {
        NlPropertyItem property = properties.getOrNull(SdkConstants.AUTO_URI, attributeName);
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel);
        }
      }
    }

    private void addSubTagSections(@NotNull InspectorPanel inspector,
                                   @NotNull MotionSelection selection,
                                   @NotNull MotionLayoutAttributesModel model) {
      XmlTag xmlTag = selection.getXmlTag(selection.getMotionSceneTag());
      XmlElementDescriptor elementDescriptor = xmlTag != null ? myDescriptorProvider.getDescriptor(xmlTag) : null;
      if (elementDescriptor == null) {
        return;
      }
      XmlElementDescriptor[] subTagDescriptors = elementDescriptor.getElementsDescriptors(xmlTag);
      for (XmlElementDescriptor descriptor : subTagDescriptors) {
        String subTagName = descriptor.getName();
        if (!subTagName.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
          boolean showConstraintWidget = subTagName.equals(MotionSceneAttrs.Tags.LAYOUT);
          SubTagAttributesModel subModel = new SubTagAttributesModel(model, subTagName);
          addPropertyTable(inspector, selection, subTagName, subModel, true, true, showConstraintWidget);
        }
      }
    }

    private void addCustomAttributes(@NotNull InspectorPanel inspector,
                                     @NotNull MotionSelection selection,
                                     @NotNull MotionLayoutAttributesModel model,
                                     boolean showDefaultValues) {
      if (!shouldDisplaySection(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, selection)) {
        return;
      }
      SubTagAttributesModel customModel = new SubTagAttributesModel(model, MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
      Function1<NlPropertyItem, Boolean> filter =
        (item) -> item.getNamespace().isEmpty() &&
                  (item.getRawValue() != null || (showDefaultValues && item.getDefaultValue() != null));
      Function1<NlPropertyItem, Unit> deleteOp = (item) -> null;

      FilteredPTableModel<NlPropertyItem> tableModel = PTableModelFactory.create(
        customModel, filter, deleteOp, PTableModelFactory.getAlphabeticalSortOrder(), Collections.emptyList(), false, true, p -> true);
      AddCustomFieldAction addFieldAction = new AddCustomFieldAction(myModel, selection);
      DeleteCustomFieldAction deleteFieldAction = new DeleteCustomFieldAction();
      List<AnAction> actions = ImmutableList.<AnAction>builder().add(addFieldAction).add(deleteFieldAction).build();

      InspectorLineModel title = inspector.addExpandableTitle("CustomAttributes", true, actions);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, actions, title);
      inspector.addComponent(new EmptyTablePanel(addFieldAction, lineModel), title);
      addFieldAction.setLineModel(lineModel);
      deleteFieldAction.setLineModel(lineModel);
    }

    private void addPropertyTable(@NotNull InspectorPanel inspector,
                                  @NotNull MotionSelection selection,
                                  @Nullable String sectionTagName,
                                  @NotNull PropertiesModel<NlPropertyItem> model,
                                  boolean showDefaultValues,
                                  boolean showSectionControl,
                                  boolean showConstraintPanel) {
      if (!shouldDisplaySection(sectionTagName, selection)) {
        return;
      }
      NlPropertyItem any = model.getProperties().getFirst();
      Function1<NlPropertyItem, Boolean> filter =
        (item) -> !item.getNamespace().isEmpty() &&
                  (item.getRawValue() != null || (showDefaultValues && item.getDefaultValue() != null));
      Function1<NlPropertyItem, Unit> deleteOp = (item) -> { item.setValue(null); return null; };

      FilteredPTableModel<NlPropertyItem> tableModel =
        PTableModelFactory.create(
          model, filter, deleteOp, PTableModelFactory.getAlphabeticalSortOrder(), Collections.emptyList(), true, true, p -> true);
      SubSectionControlAction controlAction = new SubSectionControlAction(any);
      AddMotionFieldAction addFieldAction = new AddMotionFieldAction(myModel, model.getProperties());
      DeleteMotionFieldAction deleteFieldAction = new DeleteMotionFieldAction();
      ImmutableList.Builder<AnAction> actionsBuilder = ImmutableList.builder();
      if (showSectionControl) {
        actionsBuilder.add(controlAction);
      }
      actionsBuilder.add(addFieldAction);
      actionsBuilder.add(deleteFieldAction);
      List<AnAction> actions = actionsBuilder.build();

      InspectorLineModel title = inspector.addExpandableTitle(sectionTagName, true, actions);
      if (showConstraintPanel) {
        addConstraintPanel(inspector, selection, title);
      }
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, actions, title);
      inspector.addComponent(new EmptyTablePanel(addFieldAction, lineModel), title);
      if (showSectionControl) {
        controlAction.setLineModel(title);
      }
      addFieldAction.setLineModel(lineModel);
      deleteFieldAction.setLineModel(lineModel);
    }

    private static void addConstraintPanel(@NotNull InspectorPanel inspector,
                                           @NotNull MotionSelection selection,
                                           @NotNull InspectorLineModel titleLine) {
      NlComponent component = selection.getComponent();
      List<NlComponent> components = component == null ? Collections.emptyList() : Collections.singletonList(component);
      MotionConstraintPanel panel = new MotionConstraintPanel(components);
      panel.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
      inspector.addComponent(panel, titleLine);
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

        case MotionSceneAttrs.Tags.ON_CLICK:
        case MotionSceneAttrs.Tags.ON_SWIPE:
          return selection.getType() == MotionEditorSelector.Type.TRANSITION;

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
      if (xml == null) {
        return false;
      }
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
