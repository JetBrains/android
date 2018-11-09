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
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.ConstraintSetConstraint;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.Key_framePosition;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.Key_frameTarget;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.MotionSceneConstraintSet;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.MotionSceneTransition;

import com.android.SdkConstants;
import com.android.tools.adtui.ptable2.PTableColumn;
import com.android.tools.adtui.ptable2.PTableItem;
import com.android.tools.adtui.ptable2.PTableModel;
import com.android.tools.adtui.ptable2.PTableModelUpdateListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property2.api.EditorProvider;
import com.android.tools.idea.common.property2.api.InspectorBuilder;
import com.android.tools.idea.common.property2.api.InspectorLineModel;
import com.android.tools.idea.common.property2.api.InspectorPanel;
import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.android.tools.idea.common.property2.api.PropertiesView;
import com.android.tools.idea.common.property2.api.PropertiesViewTab;
import com.android.tools.idea.common.property2.api.TableLineModel;
import com.android.tools.idea.common.property2.api.TableUIProvider;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString;
import com.android.tools.idea.uibuilder.handlers.motion.attributeEditor.NewCustomAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.model.TargetModel;
import com.android.tools.idea.uibuilder.handlers.motion.property2.ui.TargetComponent;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider;
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.formatter.AttributeComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesView} for motion layout property editor.
 */
public class MotionLayoutAttributesView extends PropertiesView<NelePropertyItem> {
  private static final String MOTION_VIEW_NAME = "Motion";

  public MotionLayoutAttributesView(@NotNull MotionLayoutAttributesModel model) {
    super(MOTION_VIEW_NAME, model);
    PropertiesViewTab<NelePropertyItem> tab = addTab("");
    NeleEnumSupportProvider enumSupportProvider = new NeleEnumSupportProvider();
    NeleControlTypeProvider controlTypeProvider = new NeleControlTypeProvider(enumSupportProvider);
    EditorProvider<NelePropertyItem> editorProvider = EditorProvider.Companion.create(enumSupportProvider, controlTypeProvider);
    TableUIProvider tableUIProvider = TableUIProvider.Companion.create(NelePropertyItem.class, controlTypeProvider, editorProvider);
    tab.getBuilders().add(new MotionInspectorBuilder(model.getFacet(), editorProvider, tableUIProvider));
  }

  private static class MotionInspectorBuilder implements InspectorBuilder<NelePropertyItem> {
    private final EditorProvider<NelePropertyItem> myEditorProvider;
    private final AttributeComparator<NelePropertyItem> myAttributeComparator;
    private final TableUIProvider myTableUIProvider;
    private final CustomPanel myCustomLayoutPanel;
    private final XmlElementDescriptorProvider myDescriptorProvider;

    private MotionInspectorBuilder(@NotNull AndroidFacet facet,
                                   @NotNull EditorProvider<NelePropertyItem> editorProvider,
                                   @NotNull TableUIProvider tableUIProvider) {
      myEditorProvider = editorProvider;
      myAttributeComparator = new AttributeComparator<>(NelePropertyItem::getName);
      myTableUIProvider = tableUIProvider;
      myCustomLayoutPanel = loadCustomLayoutPanel(facet);
      myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector,
                                  @NotNull PropertiesTable<? extends NelePropertyItem> properties) {
      NelePropertyItem any = properties.getFirst();
      if (any == null) {
        return;
      }
      XmlTag tag = MotionLayoutAttributesModel.getTag(any);
      if (tag == null) {
        return;
      }
      NlComponent component = any.getComponents().get(0);
      String label = tag.getLocalName();
      switch (label) {
        case ConstraintSetConstraint:
          NelePropertyItem targetId = properties.getOrNull(ANDROID_URI, ATTR_ID);
          label = MotionSceneConstraintSet;
          addTargetComponent(inspector, component, label);
          addCustomLayoutComponent(inspector, component);
          addPropertyTable(inspector, label, properties, targetId);
          break;

        case MotionSceneTransition:
          addTargetComponent(inspector, component, label);
          addPropertyTable(inspector, label, properties);
          break;

        default:
          // This should be some kind of KeyFrame
          NelePropertyItem target = properties.getOrNull(AUTO_URI, Key_frameTarget);
          NelePropertyItem position = properties.getOrNull(AUTO_URI, Key_framePosition);
          if (target == null || position == null) {
            // All KeyFrames should have target and position.
            Logger.getInstance(NelePropertyItem.class).warn("KeyFrame without target and position");
            return;
          }
          addTargetComponent(inspector, component, label);
          inspector.addEditor(myEditorProvider.createEditor(position, false), null);
          addPropertyTable(inspector, label, properties, target, position);
          break;
      }
      if (hasCustomAttributes(tag)) {
        addCustomAttributes(inspector, properties);
      }
    }

    @Nullable
    private static CustomPanel loadCustomLayoutPanel(@NotNull AndroidFacet facet) {
      ViewHandlerManager manager = ViewHandlerManager.get(facet);
      ViewHandler handler = manager.getHandler(SdkConstants.MOTION_LAYOUT.newName());
      return handler != null ? handler.getLayoutCustomPanel() : null;
    }

    private void addCustomLayoutComponent(@NotNull InspectorPanel inspector, @NotNull NlComponent component) {
      NlComponent parent = component.getParent();
      String parentTag = parent != null ? parent.getTagName() : null;
      if (myCustomLayoutPanel != null && SdkConstants.MOTION_LAYOUT.isEquals(parentTag)) {
        InspectorLineModel title = inspector.addExpandableTitle("layout", true);
        myCustomLayoutPanel.useComponent(component);
        inspector.addComponent(myCustomLayoutPanel.getPanel(), title);
      }
    }

    private void addCustomAttributes(@NotNull InspectorPanel inspector,
                                     @NotNull PropertiesTable<? extends NelePropertyItem> properties) {
      List<NelePropertyItem> attributes = properties.getValues().stream()
        .filter(item -> item.getNamespace().isEmpty())
        .sorted(myAttributeComparator)
        .collect(Collectors.toList());

      NelePropertyItem property = properties.getValues().stream()
        .filter(item -> !item.getNamespace().isEmpty())
        .findFirst()
        .orElse(null);

      if (property == null) {
        return;
      }

      CustomPropertyTableModel tableModel = new CustomPropertyTableModel(attributes);
      AddCustomFieldAction addFieldAction = new AddCustomFieldAction(tableModel, property);
      DeleteCustomFieldAction deleteFieldAction = new DeleteCustomFieldAction(tableModel);
      InspectorLineModel title = inspector.addExpandableTitle("CustomAttributes", true, addFieldAction, deleteFieldAction);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, title);
      deleteFieldAction.setLineModel(lineModel);
    }

    private static void addTargetComponent(@NotNull InspectorPanel inspector, @NotNull NlComponent component, @NotNull String label) {
      TargetComponent targetComponent = new TargetComponent(new TargetModel(component, label));
      inspector.addComponent(targetComponent, null);
    }

    private void addPropertyTable(@NotNull InspectorPanel inspector,
                                  @NotNull String titleName,
                                  @NotNull PropertiesTable<? extends NelePropertyItem> properties,
                                  @NotNull NelePropertyItem... excluded) {
      List<NelePropertyItem> attributes = properties.getValues().stream()
        .filter(item -> !item.getNamespace().isEmpty() && ArrayUtil.find(excluded, item) < 0)
        .sorted(myAttributeComparator)
        .collect(Collectors.toList());

      InspectorLineModel title = inspector.addExpandableTitle(titleName, true);
      inspector.addTable(new MotionTableModel(attributes), true, myTableUIProvider, title);
    }

    private boolean hasCustomAttributes(@NotNull XmlTag tag) {
      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
      if (elementDescriptor == null) {
        return false;
      }
      for (XmlElementDescriptor childDescriptor : elementDescriptor.getElementsDescriptors(tag)) {
        if (childDescriptor.getDefaultName().equals(MotionSceneString.KeyAttributes_customAttribute)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void resetCache() {
    }
  }

  /**
   * Model for a general properties table.
   *
   * Used to display a list of properties.
   * Certain key properties may be filtered out and shown separately.
   */
  private static class MotionTableModel implements PTableModel {
    private final List<PTableItem> myItems;
    private final List<PTableModelUpdateListener> myListeners;

    private MotionTableModel(@NotNull List<NelePropertyItem> items) {
      items.sort(Comparator.comparing(NelePropertyItem::getName).thenComparing(NelePropertyItem::getNamespace));
      myItems = new ArrayList<>(items);
      myListeners = new ArrayList<>();
    }

    @NotNull
    @Override
    public List<PTableItem> getItems() {
      return myItems;
    }

    @Override
    public boolean isCellEditable(@NotNull PTableItem item, @NotNull PTableColumn column) {
      return true;
    }

    @Override
    public boolean acceptMoveToNextEditor(@NotNull PTableItem item, @NotNull PTableColumn column) {
      return true;
    }

    @Override
    public void addListener(@NotNull PTableModelUpdateListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void refresh() {
      new ArrayList<>(myListeners).forEach(listener -> listener.itemsUpdated(false));
    }
  }

  private static class CustomPropertyTableModel implements PTableModel {
    private final List<PTableItem> myItems;
    private final List<PTableModelUpdateListener> myListeners;
    private final AttributeComparator<PTableItem> myAttributeComparator;

    private CustomPropertyTableModel(@NotNull List<NelePropertyItem> items) {
      myAttributeComparator = new AttributeComparator<>(PTableItem::getName);
      myItems = new ArrayList<>(items);
      myListeners = new ArrayList<>();
      myItems.sort(myAttributeComparator);
    }

    @NotNull
    @Override
    public List<PTableItem> getItems() {
      return myItems;
    }

    public void remove(@NotNull NelePropertyItem item) {
      int index = myItems.indexOf(item);
      if (index < 0) {
        return;
      }
      myItems.remove(index);
      fireUpdate(true);
    }

    public void add(@NotNull NelePropertyItem item) {
      int index = myItems.indexOf(item);
      if (index < 0) {
        myItems.add(item);
        myItems.sort(myAttributeComparator);
      }
      else {
        myItems.set(index, item);
      }
      fireUpdate(true);
    }

    @Override
    public boolean isCellEditable(@NotNull PTableItem item, @NotNull PTableColumn column) {
      return true;
    }

    @Override
    public boolean acceptMoveToNextEditor(@NotNull PTableItem item, @NotNull PTableColumn column) {
      return true;
    }

    @Override
    public void addListener(@NotNull PTableModelUpdateListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void refresh() {
      fireUpdate(false);
    }

    private void fireUpdate(boolean modelChange) {
      new ArrayList<>(myListeners).forEach(listener -> listener.itemsUpdated(modelChange));
    }
  }

  private static class AddCustomFieldAction extends AnAction {
    private final CustomPropertyTableModel myTableModel;
    private final NelePropertyItem myProperty;
    private final MotionLayoutAttributesModel myModel;

    private AddCustomFieldAction(@NotNull CustomPropertyTableModel tableModel, @NotNull NelePropertyItem property) {
      super(null, "Add Property", StudioIcons.Common.ADD);
      myTableModel = tableModel;
      myProperty = property;
      myModel = (MotionLayoutAttributesModel)myProperty.getModel();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      NewCustomAttributePanel newAttributePanel = new NewCustomAttributePanel();
      newAttributePanel.show();
      if (!newAttributePanel.isOK()) {
        return;
      }
      String attributeName = newAttributePanel.getAttributeName();
      String value = newAttributePanel.getInitialValue();
      MotionSceneModel.CustomAttributes.Type type = newAttributePanel.getType();
      if (StringUtil.isEmpty(attributeName)) {
        return;
      }
      XmlTag tag = MotionLayoutAttributesModel.getTag(myProperty);
      if (tag == null) {
        return;
      }
      Consumer<XmlTag> applyToModel = newCustomTag -> {
        NelePropertyItem newProperty = MotionLayoutPropertyProvider.createCustomProperty(
          attributeName, type.getTagName(), newCustomTag, myProperty.getModel(), myProperty.getComponents());
        myTableModel.add(newProperty);
      };

      myModel.createCustomXmlTag(tag, attributeName, value, type, applyToModel);
    }
  }

  private static class DeleteCustomFieldAction extends AnAction {
    private final CustomPropertyTableModel myTableModel;
    private TableLineModel myLineModel;

    private DeleteCustomFieldAction(@NotNull CustomPropertyTableModel tableModel) {
      super(null, "Remove Selected Property", StudioIcons.Common.REMOVE);
      myTableModel = tableModel;
    }

    public void setLineModel(@NotNull TableLineModel lineModel) {
      myLineModel = lineModel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      if (myLineModel == null) {
        return;
      }
      NelePropertyItem property = (NelePropertyItem)myLineModel.getSelectedItem();
      if (property == null) {
        return;
      }
      XmlTag tag = MotionLayoutAttributesModel.getTag(property);
      if (tag == null) {
        return;
      }
      Runnable applyToModel = () -> myTableModel.remove(property);

      MotionLayoutAttributesModel model = (MotionLayoutAttributesModel)property.getModel();
      model.deleteTag(tag, applyToModel);
    }
  }
}
