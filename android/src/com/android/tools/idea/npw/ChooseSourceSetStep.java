/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * Shows a list of source sets to choose from. Defaults to the list of source sets that
 * involve the target file, but can be expanded to show all.
 */
public class ChooseSourceSetStep extends TemplateWizardStep {
  private JCheckBox myShowAllCheckBox;
  private JBLabel myDescription;
  private JBList mySourceSetList;
  private JPanel myPanel;
  private JBScrollPane mySourceSetListScrollPane;
  private JBLabel myInstructions;
  private List<SourceProvider> myFilteredSourceProviders;
  private List<SourceProvider> myAllSourceProviders;
  private final SourceProviderSelectedListener mySelectionListener;

  public ChooseSourceSetStep(@NotNull TemplateWizardState state,
                             @NotNull Project project,
                             @NotNull Module module,
                             @Nullable Icon sidePanelIcon,
                             @Nullable UpdateListener updateListener,
                             @NotNull SourceProviderSelectedListener selectionListener,
                             @NotNull List<SourceProvider> sourceProviders) {
    super(state, project, module, sidePanelIcon, updateListener);
    mySelectionListener = selectionListener;
    mySourceSetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySourceSetListScrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
    myInstructions.setText("<html>The selected folder contains multiple source sets <br>" +
                           "(this can include source sets that do not yet exist on disk).<br>" +
                           "Please select the target source set in which to create the files.</html>");
    growLabelIfNecessary(myInstructions);

    myFilteredSourceProviders = sourceProviders;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    myAllSourceProviders = IdeaSourceProvider.getAllSourceProviders(facet);
    if (myFilteredSourceProviders.isEmpty()) {
      myFilteredSourceProviders = myAllSourceProviders;
      myShowAllCheckBox.setSelected(true);
      myShowAllCheckBox.setEnabled(false);
    }

    setSourceProviders(myFilteredSourceProviders);

    mySourceSetList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (mySourceSetList.getSelectedIndex() == -1) {
          mySourceSetList.setSelectedIndex(0);
        }
        SourceProviderListItem selectedItem = (SourceProviderListItem)mySourceSetList.getModel().getElementAt(mySourceSetList.getSelectedIndex());
        mySelectionListener.sourceProviderSelected(selectedItem.myProvider);
      }
    });
    myShowAllCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          setSourceProviders(myAllSourceProviders);
        } else {
          setSourceProviders(myFilteredSourceProviders);
        }
      }
    });
  }

  private void setSourceProviders(List<SourceProvider> sourceProviders) {
    List<SourceProviderListItem> sourceProviderListItemList = Lists.newArrayListWithCapacity(sourceProviders.size());
    for (SourceProvider provider : sourceProviders) {
      sourceProviderListItemList.add(new SourceProviderListItem(provider, provider.getName()));
    }
    mySourceSetList.setModel(JBList.createDefaultListModel(ArrayUtil.toObjectArray(sourceProviderListItemList)));
    mySourceSetList.setSelectedIndex(0);
  }

  private static class SourceProviderListItem {
    private SourceProvider myProvider;
    private String myDisplayName;

    public SourceProviderListItem(@NotNull SourceProvider provider, @NotNull String displayName) {
      myProvider = provider;
      myDisplayName = displayName;
    }

    @Override
    public String toString() {
      return myDisplayName;
    }
  }

  public interface SourceProviderSelectedListener {
    void sourceProviderSelected(@NotNull SourceProvider sourceProvider);
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myDescription;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }
}
