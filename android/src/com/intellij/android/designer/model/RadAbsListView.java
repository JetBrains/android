/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.Features;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.LayoutMetadata;
import com.android.tools.idea.rendering.RenderService;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.android.uipreview.RecyclerViewHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.LayoutMetadata.*;

public class RadAbsListView extends RadViewComponent {

  @Override
  public boolean addPopupActions(@NotNull AndroidDesignerEditorPanel designer,
                                 @NotNull DefaultActionGroup beforeGroup,
                                 @NotNull DefaultActionGroup afterGroup,
                                 @Nullable JComponent shortcuts,
                                 @NotNull List<RadComponent> selection) {
    super.addPopupActions(designer, beforeGroup, afterGroup, shortcuts, selection);

    boolean isRecyclerView = RecyclerViewHelper.CN_RECYCLER_VIEW.equals(getTag().getName());
    int testCapability = isRecyclerView ? Features.RECYCLER_VIEW_ADAPTER : Features.ADAPTER_BINDING;
    Configuration configuration = designer.getConfiguration();
    Module module = designer.getModule();
    IAndroidTarget target = configuration.getTarget();
    if (target != null && RenderService.supportsCapability(module, target, testCapability)) {
      beforeGroup.add(createListTypeAction(designer));
      beforeGroup.addSeparator();
    }

    return true;
  }

  private DefaultActionGroup createListTypeAction(AndroidDesignerEditorPanel designer) {
    XmlTag tag = getTag();
    String tagName = tag.getName();
    boolean isSpinner = tagName.equals(SPINNER);
    boolean isGridView = tagName.equals(GRID_VIEW);
    boolean isRecyclerView = tagName.equals(RecyclerViewHelper.CN_RECYCLER_VIEW);
    String previewType = isGridView ? "Preview Grid Content" : isSpinner ? "Preview Spinner Layout" : "Preview List Content";

    String selected = tag.getAttributeValue(KEY_LV_ITEM, TOOLS_URI);
    if (selected != null) {
      if (selected.startsWith(ANDROID_LAYOUT_RESOURCE_PREFIX)) {
        selected = selected.substring(ANDROID_LAYOUT_RESOURCE_PREFIX.length());
      }
    }

    DefaultActionGroup previewGroup = new DefaultActionGroup("_" + previewType, true);

    previewGroup.add(new PickLayoutAction(designer, "Choose Item Layout...", KEY_LV_ITEM));
    previewGroup.addSeparator();

    if (isSpinner) {
      previewGroup.add(new SetListTypeAction(designer, "Spinner Item", "simple_spinner_item", selected));
      previewGroup.add(new SetListTypeAction(designer, "Spinner Dropdown Item", "simple_spinner_dropdown_item", selected));
    }
    else {
      previewGroup.add(new SetListTypeAction(designer, "Simple List Item", "simple_list_item_1", selected));

      previewGroup.add(new SetListTypeAction(designer, "Simple 2-Line List Item", "simple_list_item_2", selected));
      previewGroup.add(new SetListTypeAction(designer, "Checked List Item", "simple_list_item_checked", selected));
      previewGroup.add(new SetListTypeAction(designer, "Single Choice List Item", "simple_list_item_single_choice", selected));
      previewGroup.add(new SetListTypeAction(designer, "Multiple Choice List Item", "simple_list_item_multiple_choice", selected));

      if (!isGridView) {
        previewGroup.addSeparator();
        previewGroup.add(new SetListTypeAction(designer, "Simple Expandable List Item", "simple_expandable_list_item_1", selected));
        previewGroup.add(new SetListTypeAction(designer, "Simple 2-Line Expandable List Item", "simple_expandable_list_item_2", selected));

        if (!isRecyclerView) {
          previewGroup.addSeparator();
          previewGroup.add(new PickLayoutAction(designer, "Choose Header...", KEY_LV_HEADER));
          previewGroup.add(new PickLayoutAction(designer, "Choose Footer...", KEY_LV_FOOTER));
        }
      }
    }

    return previewGroup;
  }

  private class PickLayoutAction extends AnAction {
    private final AndroidDesignerEditorPanel myPanel;
    private final String myType;

    private PickLayoutAction(AndroidDesignerEditorPanel panel, String title, String type) {
      super(title);
      myPanel = panel;
      myType = type;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ChooseResourceDialog dialog = new ChooseResourceDialog(myPanel.getModule(), new ResourceType[]{ResourceType.LAYOUT}, null, null);
      dialog.setAllowCreateResource(false);
      if (dialog.showAndGet()) {
        String layout = dialog.getResourceName();
        setNewType(myPanel, myType, layout);
      }
    }
  }

  private class SetListTypeAction extends AnAction {
    private final AndroidDesignerEditorPanel myPanel;
    private final String myLayout;

    public SetListTypeAction(@NotNull AndroidDesignerEditorPanel panel,
                             @NotNull String title,
                             @NotNull String layout,
                             @Nullable String selected) {
      super(title);
      myPanel = panel;
      myLayout = layout;

      if (layout.equals(selected)) {
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.putClientProperty(Toggleable.SELECTED_PROPERTY, selected);
        templatePresentation.setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      setNewType(myPanel, KEY_LV_ITEM, ANDROID_LAYOUT_RESOURCE_PREFIX + myLayout);
    }
  }

  private void setNewType(@NotNull AndroidDesignerEditorPanel panel,
                          @NotNull String type,
                          @Nullable String layout) {
    LayoutMetadata.setProperty(panel.getProject(), "Set List Type", panel.getXmlFile(), getTag(), type, TOOLS_URI, layout);
    panel.requestRender();
  }
}
