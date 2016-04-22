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
import com.android.tools.idea.uibuilder.property.renderer.NlDefaultRenderer;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
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
import sun.awt.CausedFocusEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NlReferenceEditor implements NlComponentEditor {
  private static final int SPACING = 4;

  private final EditingListener myListener;
  private final boolean myIncludeBrowseButton;
  private final JPanel myPanel;
  private final JLabel myIconLabel;
  private final TextFieldWithAutoCompletion myTextFieldWithAutoCompletion;
  private final CompletionProvider myCompletionProvider;
  private final FixedSizeButton myBrowseButton;

  private NlProperty myProperty;
  private String myLastReadValue;
  private JLabel myLabel;

  public interface EditingListener {
    void stopEditing(@NotNull NlReferenceEditor editor, @NotNull String value);
    void cancelEditing(@NotNull NlReferenceEditor editor);
  }

  public static NlReferenceEditor createForTable(@NotNull Project project, @NotNull EditingListener listener) {
    return new NlReferenceEditor(project, listener, true);
  }

  public static NlReferenceEditor createForInspector(@NotNull Project project, @NotNull EditingListener listener) {
    return new NlReferenceEditor(project, listener, false);
  }

  public static NlReferenceEditor createForInspectorWithBrowseButton(@NotNull Project project, @NotNull EditingListener listener) {
    return new NlReferenceEditor(project, listener, true);
  }

  private NlReferenceEditor(@NotNull Project project, @NotNull EditingListener listener, boolean includeBrowseButton) {
    myIncludeBrowseButton = includeBrowseButton;
    myListener = listener;
    myPanel = new JPanel(new BorderLayout(SystemInfo.isMac ? 0 : 2, 0));

    myIconLabel = new JBLabel();
    myPanel.add(myIconLabel, BorderLayout.LINE_START);
    myIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        displayResourcePicker();
      }
    });

    myCompletionProvider = new CompletionProvider();
    myTextFieldWithAutoCompletion = new TextFieldWithAutoCompletion<>(project, myCompletionProvider, true, null);
    myTextFieldWithAutoCompletion.setBorder(BorderFactory.createEmptyBorder(0, SPACING, 0, SPACING));
    myPanel.add(myTextFieldWithAutoCompletion, BorderLayout.CENTER);

    myBrowseButton = new FixedSizeButton(new JBCheckBox());
    myBrowseButton.setToolTipText(UIBundle.message("component.with.browse.button.browse.button.tooltip.text"));
    myPanel.add(myBrowseButton, BorderLayout.LINE_END);

    myTextFieldWithAutoCompletion.registerKeyboardAction(event -> stopEditing(myTextFieldWithAutoCompletion.getDocument().getText()),
                                                         KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myTextFieldWithAutoCompletion.registerKeyboardAction(event -> displayResourcePicker(),
                                                         KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_MASK),
                                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myTextFieldWithAutoCompletion.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent focusEvent) {
        selectTextOnFocusGain(focusEvent);
      }

      @Override
      public void focusLost(FocusEvent event) {
        stopEditing(myTextFieldWithAutoCompletion.getDocument().getText());
      }
    });

    myBrowseButton.addActionListener(event -> displayResourcePicker());
  }

  private static void selectTextOnFocusGain(@NotNull FocusEvent focusEvent) {
    Object source = focusEvent.getSource();
    if (source instanceof EditorComponentImpl && focusEvent instanceof CausedFocusEvent) {
      CausedFocusEvent causedFocusEvent = (CausedFocusEvent)focusEvent;
      EditorComponentImpl editorComponent = (EditorComponentImpl)source;
      if (causedFocusEvent.getCause() == CausedFocusEvent.Cause.ACTIVATION) {
        Editor editor = editorComponent.getEditor();
        editor.getSelectionModel().setSelection(0, editor.getDocument().getTextLength());
      }
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    myTextFieldWithAutoCompletion.setEnabled(enabled);
    myBrowseButton.setVisible(enabled && myIncludeBrowseButton);
  }

  @Override
  public NlProperty getProperty() {
    return myProperty;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (myProperty != property) {
      myProperty = property;
      myLastReadValue = null;

      myBrowseButton.setVisible(myIncludeBrowseButton && hasResourceChooser(myProperty));
      myCompletionProvider.updateCompletions(myProperty);
    }

    Icon icon = NlDefaultRenderer.getIcon(myProperty);
    myIconLabel.setIcon(icon);
    myIconLabel.setVisible(icon != null);

    String propValue = StringUtil.notNullize(myProperty.getValue());
    if (!propValue.equals(myLastReadValue)) {
      myLastReadValue = propValue;
      myTextFieldWithAutoCompletion.setText(propValue);
    }
  }

  @Override
  public void refresh() {
    if (myProperty != null) {
      setProperty(myProperty);
    }
  }

  @Override
  public void requestFocus() {
    myTextFieldWithAutoCompletion.requestFocus();
  }

  @Override
  @Nullable
  public JLabel getLabel() {
    return myLabel;
  }

  @Override
  public void setLabel(@NotNull JLabel label) {
    myLabel = label;
  }

  @Override
  public void setVisible(boolean visible) {
    myPanel.setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  public Object getValue() {
    return myTextFieldWithAutoCompletion.getDocument().getText();
  }

  private void cancelEditing() {
    myListener.cancelEditing(this);
  }

  private void stopEditing(@NotNull String newValue) {
    myProperty.setValue(newValue);
    myListener.stopEditing(this, newValue);
  }

  private void displayResourcePicker() {
    ChooseResourceDialog dialog = showResourceChooser(myProperty);
    if (dialog.showAndGet()) {
      stopEditing(dialog.getResourceName());
    } else {
      cancelEditing();
    }
  }

  public static boolean hasResourceChooser(@NotNull NlProperty p) {
    return getResourceTypes(p.getDefinition()).length > 0;
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
    return type == null ? AttributeFormat.convertTypes(formats) : new ResourceType[]{type};
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
      return resItems.stream().map(ResourceItem::getResourceUrl).collect(Collectors.toList());
    }
  }
}
