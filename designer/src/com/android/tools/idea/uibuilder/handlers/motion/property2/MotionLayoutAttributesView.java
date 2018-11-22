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

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property2.api.EditorProvider;
import com.android.tools.idea.common.property2.api.FilteredPTableModel;
import com.android.tools.idea.common.property2.api.InspectorBuilder;
import com.android.tools.idea.common.property2.api.InspectorLineModel;
import com.android.tools.idea.common.property2.api.InspectorPanel;
import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.android.tools.idea.common.property2.api.PropertiesView;
import com.android.tools.idea.common.property2.api.TableLineModel;
import com.android.tools.idea.common.property2.api.TableUIProvider;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.AddCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.AddMotionFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.DeleteCustomFieldAction;
import com.android.tools.idea.uibuilder.handlers.motion.property2.action.DeleteMotionFieldAction;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.property2.model.SelectedComponentModel;
import com.android.tools.idea.uibuilder.property2.support.NeleEnumSupportProvider;
import com.android.tools.idea.uibuilder.property2.support.NeleTwoStateBooleanControlTypeProvider;
import com.android.tools.idea.uibuilder.property2.ui.SelectedComponentPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import java.util.Collections;
import kotlin.jvm.functions.Function1;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesView} for motion layout property editor.
 */
public class MotionLayoutAttributesView extends PropertiesView<NelePropertyItem> {
  private static final String MOTION_VIEW_NAME = "Motion";

  public MotionLayoutAttributesView(@NotNull MotionLayoutAttributesModel model) {
    super(MOTION_VIEW_NAME, model);
    NeleEnumSupportProvider enumSupportProvider = new NeleEnumSupportProvider();
    NeleTwoStateBooleanControlTypeProvider controlTypeProvider = new NeleTwoStateBooleanControlTypeProvider(enumSupportProvider);
    EditorProvider<NelePropertyItem> editorProvider = EditorProvider.Companion.create(enumSupportProvider, controlTypeProvider);
    TableUIProvider tableUIProvider = TableUIProvider.Companion.create(NelePropertyItem.class, controlTypeProvider, editorProvider);
    getMain().getBuilders().add(new SelectedTargetBuilder());
    addTab("").getBuilders().add(new MotionInspectorBuilder(model, editorProvider, tableUIProvider));
  }

  private static class SelectedTargetBuilder implements InspectorBuilder<NelePropertyItem> {

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector, @NotNull PropertiesTable<? extends NelePropertyItem> properties) {
      NelePropertyItem any = properties.getFirst();
      if (any == null || any.getComponents().isEmpty()) {
        return;
      }
      NlComponent component = any.getComponents().get(0);
      XmlTag tag = MotionLayoutAttributesModel.getTag(any);
      if (tag == null) {
        return;
      }
      String label = tag.getLocalName();
      inspector.addComponent(new SelectedComponentPanel(new SelectedComponentModel(Collections.singletonList(component), label)), null);
    }

    @Override
    public void resetCache() {
    }
  }

  private static class MotionInspectorBuilder implements InspectorBuilder<NelePropertyItem> {
    private final MotionLayoutAttributesModel myModel;
    private final EditorProvider<NelePropertyItem> myEditorProvider;
    private final TableUIProvider myTableUIProvider;
    private final CustomPanel myCustomLayoutPanel;
    private final XmlElementDescriptorProvider myDescriptorProvider;

    private MotionInspectorBuilder(@NotNull MotionLayoutAttributesModel model,
                                   @NotNull EditorProvider<NelePropertyItem> editorProvider,
                                   @NotNull TableUIProvider tableUIProvider) {
      myModel = model;
      myEditorProvider = editorProvider;
      myTableUIProvider = tableUIProvider;
      myCustomLayoutPanel = loadCustomLayoutPanel(model.getFacet());
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
        case MotionSceneString.ConstraintSetConstraint:
          NelePropertyItem targetId = properties.getOrNull(ANDROID_URI, ATTR_ID);
          label = MotionSceneString.MotionSceneConstraintSet;
          addCustomLayoutComponent(inspector, component);
          addPropertyTable(inspector, label, properties, targetId);
          break;

        case MotionSceneString.MotionSceneTransition:
          addPropertyTable(inspector, label, properties);
          break;

        default:
          // This should be some kind of KeyFrame
          NelePropertyItem target = properties.getOrNull(AUTO_URI, MotionSceneString.Key_frameTarget);
          NelePropertyItem position = properties.getOrNull(AUTO_URI, MotionSceneString.Key_framePosition);
          if (target == null || position == null) {
            // All KeyFrames should have target and position.
            Logger.getInstance(NelePropertyItem.class).warn("KeyFrame without target and position");
            return;
          }
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
      NelePropertyItem property = properties.getValues().stream()
        .filter(item -> !item.getNamespace().isEmpty())
        .findFirst()
        .orElse(null);

      if (property == null) {
        return;
      }

      Function1<NelePropertyItem, Boolean> filter = (item) -> item.getNamespace().isEmpty();
      FilteredPTableModel<NelePropertyItem> tableModel =
        FilteredPTableModel.Companion.create(myModel, filter, Collections.emptyList(), false);
      AddCustomFieldAction addFieldAction = new AddCustomFieldAction(tableModel, property);
      DeleteCustomFieldAction deleteFieldAction = new DeleteCustomFieldAction(tableModel);
      InspectorLineModel title = inspector.addExpandableTitle("CustomAttributes", true, addFieldAction, deleteFieldAction);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, title);
      deleteFieldAction.setLineModel(lineModel);
    }

    private void addPropertyTable(@NotNull InspectorPanel inspector,
                                  @NotNull String titleName,
                                  @NotNull PropertiesTable<? extends NelePropertyItem> properties,
                                  @NotNull NelePropertyItem... excluded) {
      Function1<NelePropertyItem, Boolean> filter =
        (item) -> !item.getNamespace().isEmpty() && ArrayUtil.find(excluded, item) < 0 && item.getRawValue() != null;
      FilteredPTableModel<NelePropertyItem> tableModel =
        FilteredPTableModel.Companion.create(myModel, filter, Collections.emptyList(), true);
      AddMotionFieldAction addFieldAction = new AddMotionFieldAction(myModel, tableModel, properties);
      DeleteMotionFieldAction deleteFieldAction = new DeleteMotionFieldAction(tableModel);
      InspectorLineModel title = inspector.addExpandableTitle(titleName, true, addFieldAction, deleteFieldAction);
      TableLineModel lineModel = inspector.addTable(tableModel, true, myTableUIProvider, title);
      addFieldAction.setLineModel(lineModel);
      deleteFieldAction.setLineModel(lineModel);
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
}
