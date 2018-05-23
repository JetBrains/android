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

import com.android.tools.adtui.ptable2.PTableItem;
import com.android.tools.adtui.ptable2.PTableModel;
import com.android.tools.adtui.ptable2.PTableColumn;
import com.android.tools.idea.common.property2.api.*;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.*;

public class MotionLayoutAttributesView {

  public static PropertiesView<MotionPropertyItem> createMotionView(@NotNull MotionLayoutAttributesModel model) {
    PropertiesView<MotionPropertyItem> view = new PropertiesView<>(model);
    PropertiesViewTab<MotionPropertyItem> tab = view.addTab("");
    EditorProvider<MotionPropertyItem> editorProvider =
      EditorProvider.Companion.create(new MotionEnumSupportProvider(), new MotionControlTypeProvider());
    tab.getBuilders().add(new KeyFrameInspectorBuilder(editorProvider));
    return view;
  }

  private static class KeyFrameInspectorBuilder implements InspectorBuilder<MotionPropertyItem> {
    private final EditorProvider<MotionPropertyItem> myEditorProvider;

    private KeyFrameInspectorBuilder(EditorProvider<MotionPropertyItem> editorProvider) {
      myEditorProvider = editorProvider;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector, @NotNull PropertiesTable<? extends MotionPropertyItem> properties) {
      MotionPropertyItem target = properties.getOrNull(AUTO_URI, Key_frameTarget);
      MotionPropertyItem position = properties.getOrNull(AUTO_URI, Key_framePosition);
      if (target == null || position == null) {
        return;
      }
      inspector.addEditor(myEditorProvider.createEditor(position, false));

      Set<String> excluded = ImmutableSet.of(Key_frameTarget, Key_framePosition);
      MotionSceneModel.BaseTag tag = position.getTag();
      InspectorLineModel title = inspector.addExpandableTitle(tag.getTitle(), true);

      List<MotionPropertyItem> attributes = properties.getValues().stream()
                                                      .filter(item -> !excluded.contains(item.getName()))
                                                      .collect(Collectors.toList());
      title.addChild(inspector.addTable(new MotionTableModel(attributes), true));
    }

    @Override
    public void resetCache() {
    }
  }

  private static class MotionTableModel implements PTableModel {
    private final List<PTableItem> myItems;

    private MotionTableModel(@NotNull List<MotionPropertyItem> items) {
      items.sort(Comparator.comparing(MotionPropertyItem::getName).thenComparing(MotionPropertyItem::getNamespace));
      myItems = new ArrayList<>(items);
    }

    @NotNull
    @Override
    public List<PTableItem> getItems() {
      return myItems;
    }

    @Override
    public boolean isCellEditable(@NotNull PTableItem item, @NotNull PTableColumn column) {
      return false;
    }
  }
}
