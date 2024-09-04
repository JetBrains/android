/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.JBUI;
import javax.swing.JComponent;
import javax.swing.ListSelectionModel;

/**
 * A sidebar list to choose the workspace type.
 *
 * <p>Similar to {@link com.intellij.ide.projectWizard.ProjectTemplateList} (which isn't packaged
 * with all IDEs).
 */
public class WorkspaceTypeList extends JBList<TopLevelSelectWorkspaceOption> {

  public WorkspaceTypeList(ImmutableList<TopLevelSelectWorkspaceOption> supportedOptions) {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setModel(new CollectionListModel<>(supportedOptions));
    setCellRenderer(
        new GroupedItemsListRenderer<TopLevelSelectWorkspaceOption>(new ItemDescriptor()) {
          @Override
          protected JComponent createItemComponent() {
            JComponent component = super.createItemComponent();
            myTextLabel.setBorder(JBUI.Borders.empty(3));
            return component;
          }
        });
  }

  private static class ItemDescriptor
      extends ListItemDescriptorAdapter<TopLevelSelectWorkspaceOption> {
    @Override
    public String getTextFor(TopLevelSelectWorkspaceOption value) {
      return value.getTitle();
    }

    @Override
    public String getTooltipFor(TopLevelSelectWorkspaceOption value) {
      return value.getDescription();
    }
  }
}
