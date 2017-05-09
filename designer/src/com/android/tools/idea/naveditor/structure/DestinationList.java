/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.structure;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import icons.AndroidIcons;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Left panel for the nav editor, showing a list of available destinations.
 */
public class DestinationList implements ToolContent<DesignSurface> {

  private final JPanel myPanel;

  @VisibleForTesting
  final List<NlComponent> myComponentList = new ArrayList<>();
  private NavigationSchema mySchema;
  private ResourceResolver myResourceResolver;

  @VisibleForTesting
  SelectionModel mySelectionModel;

  private final JBList<NlComponent> myList;
  private boolean mySelectionUpdating;
  private SelectionListener mySelectionModelListener;
  private ModelListener myModelListener;
  private NlModel myModel;
  private ListSelectionListener myListSelectionListener;

  private DestinationList() {
    myPanel = new JPanel(new BorderLayout());
    myList = new JBList<>(new DestinationListModel());
    myList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index,
                                                    final boolean isSelected,
                                                    final boolean cellHasFocus) {
        final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        NlComponent component = (NlComponent)value;
        String label = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL);
        if (label != null) {
          label = ResourceHelper.resolveStringValue(myResourceResolver, label);
        }
        if (label == null) {
          label = component.getId();
        }
        setText(label);
        Icon icon = AndroidIcons.NavEditorIcons.Destination;
        if (mySchema.getDestinationType(component.getTagName()) == NavigationSchema.DestinationType.NAVIGATION) {
          icon = AndroidIcons.NavEditorIcons.DestinationGroup;
        }
        setIcon(icon);
        return result;
      }
    });
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myList, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    pane.setBorder(null);
    myPanel.add(pane, BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    mySelectionModel.removeListener(mySelectionModelListener);
    myModel.removeListener(myModelListener);
    myList.removeListSelectionListener(myListSelectionListener);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface toolContext) {
    if (mySelectionModel != null && mySelectionModelListener != null) {
      mySelectionModel.removeListener(mySelectionModelListener);
    }
    if (myList != null && myListSelectionListener != null) {
      myList.removeListSelectionListener(myListSelectionListener);
    }
    if (myModel != null && myModelListener != null) {
      myModel.removeListener(myModelListener);
    }

    if (toolContext != null) {
      myModel = toolContext.getModel();
      mySchema = NavigationSchema.getOrCreateSchema(myModel.getFacet());
      mySelectionModel = toolContext.getSelectionModel();
      mySelectionModelListener = (model, selection) -> {
        if (mySelectionUpdating) {
          return;
        }
        try {
          mySelectionUpdating = true;
          Set<NlComponent> components = new HashSet<>(mySelectionModel.getSelection());
          int i = 0;
          List<Integer> selectedIndices = new ArrayList<>();
          for (NlComponent component : myComponentList) {
            if (components.contains(component)) {
              selectedIndices.add(i);
            }
            i++;
          }
          myList.setSelectedIndices(ArrayUtil.toIntArray(selectedIndices));
        }
        finally {
          mySelectionUpdating = false;
        }
      };
      mySelectionModel.addListener(mySelectionModelListener);
      myListSelectionListener = e -> {
        if (mySelectionUpdating) {
          return;
        }
        try {
          mySelectionUpdating = true;
          mySelectionModel.setSelection(myList.getSelectedValuesList());
        }
        finally {
          mySelectionUpdating = false;
        }
      };
      myList.addListSelectionListener(myListSelectionListener);
      myModelListener = new ModelListener() {
        @Override
        public void modelRendered(@NotNull NlModel model) {
        }

        @Override
        public void modelChanged(@NotNull NlModel model) {
          updateComponentList(toolContext);
        }

        @Override
        public void modelActivated(@NotNull NlModel model) {
          updateComponentList(toolContext);
        }

        @Override
        public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
        }
      };
      myModel.addListener(myModelListener);
      myResourceResolver = toolContext.getConfiguration().getResourceResolver();
    }
    updateComponentList(toolContext);
  }

  private void updateComponentList(@Nullable DesignSurface toolContext) {
    myComponentList.clear();
    if (toolContext != null) {
      NlModel model = toolContext.getModel();
      NlComponent root = model.getComponents().get(0);
      for (NlComponent child : root.getChildren()) {
        if (mySchema.getDestinationType(child.getTagName()) != null) {
          myComponentList.add(child);
        }
      }
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private class DestinationListModel extends AbstractListModel<NlComponent> {

    @Override
    public int getSize() {
      return myComponentList.size();
    }

    @Override
    public NlComponent getElementAt(int index) {
      return myComponentList.get(index);
    }
  }

  public static class DestinationListDefinition extends ToolWindowDefinition<DesignSurface> {
    public DestinationListDefinition() {
      super("Destinations", AllIcons.Toolwindows.ToolWindowHierarchy, "destinations", Side.LEFT, Split.TOP, AutoHide.DOCKED,
            DestinationList::new);
    }
  }
}
