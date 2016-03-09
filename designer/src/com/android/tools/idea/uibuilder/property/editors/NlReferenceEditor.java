/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.ui.resourcechooser.ResourceGroup;
import com.android.tools.idea.ui.resourcechooser.ResourceItem;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellEditor;
import com.android.tools.idea.uibuilder.property.renderer.NlDefaultRenderer;
import com.google.common.collect.Lists;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import icons.AndroidIcons;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class NlReferenceEditor extends PTableCellEditor implements ActionListener {
  private final JPanel myPanel;
  private final JBLabel myLabel;
  private final TextFieldWithAutoCompletion myTextFieldWithAutoCompletion;
  private final CompletionProvider myCompletionProvider;
  private final FixedSizeButton myBrowseButton;

  private NlProperty myProperty;
  private String myValue;

  public NlReferenceEditor(@NotNull Project project) {
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myLabel = new JBLabel();
    myPanel.add(myLabel, BorderLayout.LINE_START);

    myCompletionProvider = new CompletionProvider();
    myTextFieldWithAutoCompletion = new TextFieldWithAutoCompletion<String>(project, myCompletionProvider, false, null);
    myPanel.add(myTextFieldWithAutoCompletion, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myTextFieldWithAutoCompletion
      .registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    selectTextOnFocusGain(myTextFieldWithAutoCompletion);

    myBrowseButton.addActionListener(this);
  }

  public static void selectTextOnFocusGain(EditorTextField textField) {
    textField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent focusEvent) {
        Object source = focusEvent.getSource();
        if (source instanceof EditorComponentImpl) {
          EditorImpl editor = ((EditorComponentImpl)source).getEditor();
          editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
        }
      }
    });
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    assert value instanceof NlProperty;
    myProperty = (NlProperty)value;

    Icon icon = NlDefaultRenderer.getIcon(myProperty);
    myLabel.setIcon(icon);
    myLabel.setVisible(icon != null);

    String propValue = StringUtil.notNullize(myProperty.getValue());
    myValue = propValue;
    myTextFieldWithAutoCompletion.setText(propValue);
    myCompletionProvider.updateCompletions(myProperty);

    return myPanel;
  }

  @Override
  public Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myBrowseButton) {
      ChooseResourceDialog dialog = showResourceChooser(myProperty);
      if (dialog.showAndGet()) {
        myValue = dialog.getResourceName();
        stopCellEditing();
      } else {
        cancelCellEditing();
      }
    }
    else if (e.getSource() == myTextFieldWithAutoCompletion) {
      myValue = myTextFieldWithAutoCompletion.getDocument().getText();
      stopCellEditing();
    }
  }

  public static ChooseResourceDialog showResourceChooser(@NotNull NlProperty p) {
    Module m = p.getComponent().getModel().getModule();
    AttributeDefinition definition = p.getDefinition();
    ResourceType[] types = getResourceTypes(definition);
    return new ChooseResourceDialog(m, types, p.getValue(), p.getComponent().getTag());
  }

  @NotNull
  private static ResourceType[] getResourceTypes(@Nullable AttributeDefinition definition) {
    Set<AttributeFormat> formats = definition != null ? definition.getFormats() : EnumSet.allOf(AttributeFormat.class);
    // for some special known properties, we can narrow down the possible types (rather than the all encompassing reference type)
    ResourceType type = definition != null ? AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(definition.getName()) : null;
    return type == null ? ResourceEditor.convertTypes(formats) : new ResourceType[]{type};
  }

  private static class CompletionProvider extends TextFieldWithAutoCompletionListProvider<String> {
    protected CompletionProvider() {
      super(null);
    }

    @Nullable
    @Override
    public PrefixMatcher createPrefixMatcher(@NotNull String prefix) {
      return new CamelHumpMatcher(prefix);
    }

    @Nullable
    @Override
    protected Icon getIcon(@NotNull String item) {
      return item.startsWith(SdkConstants.ANDROID_PREFIX) ? AndroidIcons.Android : null;
    }

    @NotNull
    @Override
    protected String getLookupString(@NotNull String item) {
      return item;
    }

    @Nullable
    @Override
    protected String getTailText(@NotNull String item) {
      return null;
    }

    @Nullable
    @Override
    protected String getTypeText(@NotNull String item) {
      return null;
    }

    @Override
    public int compare(String item1, String item2) {
      return StringUtil.compare(item1, item2, false);
    }

    public void updateCompletions(@NotNull NlProperty p) {
      AttributeDefinition definition = p.getDefinition();
      if (definition == null) {
        setItems(null);
        return;
      }

      ResourceType[] types = getResourceTypes(definition);
      List<String> items = Lists.newArrayList();

      AndroidFacet facet = p.getComponent().getModel().getFacet();

      for (ResourceType type : types) {
        List<ResourceItem> resItems =
          new ResourceGroup(ChooseResourceDialog.APP_NAMESPACE_LABEL, type, facet, null, true).getItems();
        items.addAll(getResNames(resItems));
      }

      for (ResourceType type : types) {
        List<ResourceItem> resItems =
          new ResourceGroup(SdkConstants.ANDROID_NS_NAME, type, facet, SdkConstants.ANDROID_NS_NAME, true).getItems();
        items.addAll(getResNames(resItems));
      }

      setItems(items);
    }

    @NotNull
    private static List<String> getResNames(List<ResourceItem> resItems) {
      List<String> result = Lists.newArrayListWithExpectedSize(resItems.size());
      for (ResourceItem item : resItems) {
        result.add(item.getResourceUrl());
      }
      return result;
    }
  }
}
