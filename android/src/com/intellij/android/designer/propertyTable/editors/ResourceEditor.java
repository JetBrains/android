/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.propertyTable.editors;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.intellij.android.designer.model.RadModelBuilder;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadPropertyContext;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.editors.ComboEditor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class ResourceEditor extends PropertyEditor {
  private final ResourceType[] myTypes;
  protected ComponentWithBrowseButton myEditor;
  protected RadComponent myRootComponent;
  protected RadComponent myComponent;
  private JCheckBox myCheckBox;
  private final Border myCheckBoxBorder = new JTextField().getBorder();
  private boolean myIgnoreCheckBoxValue;
  private String myBooleanResourceValue;
  private final boolean myIsDimension;
  private final boolean myIsString;

  public ResourceEditor(Set<AttributeFormat> formats, String[] values) {
    this(convertTypes(formats), formats, values);
  }

  public ResourceEditor(@Nullable ResourceType[] types, Set<AttributeFormat> formats, @Nullable String[] values) {
    myTypes = types;
    myIsDimension = formats.contains(AttributeFormat.Dimension);
    myIsString = formats.contains(AttributeFormat.String);

    if (formats.contains(AttributeFormat.Boolean)) {
      myCheckBox = new JCheckBox();
      myEditor = new ComponentWithBrowseButton<JCheckBox>(myCheckBox, null) {
        @Override
        public Dimension getPreferredSize() {
          return getComponentPreferredSize();
        }
      };
      myCheckBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          if (!myIgnoreCheckBoxValue) {
            myBooleanResourceValue = null;
            fireValueCommitted(false, true);
          }
        }
      });
    }
    else if (formats.contains(AttributeFormat.Enum)) {
      ComboboxWithBrowseButton editor = new ComboboxWithBrowseButton(SystemInfo.isWindows ? new MyComboBox() : new ComboBox()) {
        @Override
        public Dimension getPreferredSize() {
          return getComponentPreferredSize();
        }
      };

      final JComboBox comboBox = editor.getComboBox();
      assert values != null;
      DefaultComboBoxModel model = new DefaultComboBoxModel(values);
      model.insertElementAt(StringsComboEditor.UNSET, 0);
      comboBox.setModel(model);
      comboBox.setEditable(true);
      ComboEditor.installListeners(comboBox, new ComboEditor.ComboEditorListener(this) {
        @Override
        protected void onValueChosen() {
          if (comboBox.getSelectedItem() == StringsComboEditor.UNSET) {
            comboBox.setSelectedItem(null);
          }
          super.onValueChosen();
        }
      });
      myEditor = editor;
      comboBox.setSelectedIndex(0);
    }
    else {
      myEditor = new TextFieldWithBrowseButton() {
        @Override
        protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor, @Nullable Disposable parent) {
        }

        @Override
        public Dimension getPreferredSize() {
          return getComponentPreferredSize();
        }
      };
      myEditor.registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

      JTextField textField = getComboText();
      assert textField != null;
      textField.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          fireValueCommitted(false, true);
        }
      });
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(final DocumentEvent e) {
          preferredSizeChanged();
        }
      });
      selectTextOnFocusGain(textField);
    }

    if (myCheckBox == null) {
      myEditor.registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    myEditor.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showDialog();
      }
    });
    myEditor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        myEditor.getChildComponent().requestFocus();
      }
    });
    myEditor.getButton().setSize(22);
    myEditor.getButton().setAttachedComponent(null);
  }

  public static void selectTextOnFocusGain(JTextField textField) {
    textField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent focusEvent) {
        Object source = focusEvent.getSource();
        if (source instanceof JTextField) {
          ((JTextField)source).selectAll();
        }
      }
    });
  }

  private Dimension getComponentPreferredSize() {
    Dimension size1 = myEditor.getChildComponent().getPreferredSize();
    Dimension size2 = myEditor.getButton().getPreferredSize();
    return new Dimension(Math.max(size1.width, JBUI.scale(25)) + JBUI.scale(5) + size2.width, size1.height);
  }

  public static ResourceType[] convertTypes(Set<AttributeFormat> formats) {
    Set<ResourceType> types = EnumSet.noneOf(ResourceType.class);
    for (AttributeFormat format : formats) {
      switch (format) {
        case Boolean:
          types.add(ResourceType.BOOL);
          break;
        case Color:
          types.add(ResourceType.COLOR);
          types.add(ResourceType.DRAWABLE);
          types.add(ResourceType.MIPMAP);
          break;
        case Dimension:
          types.add(ResourceType.DIMEN);
          break;
        case Integer:
          types.add(ResourceType.INTEGER);
          break;
        case Fraction:
          types.add(ResourceType.FRACTION);
          break;
        case String:
          types.add(ResourceType.STRING);
          break;
        case Reference:
          types.add(ResourceType.COLOR);
          types.add(ResourceType.DRAWABLE);
          types.add(ResourceType.MIPMAP);
          types.add(ResourceType.STRING);
          types.add(ResourceType.ID);
          types.add(ResourceType.STYLE);
          break;
        default:
          break;
      }
    }

    return types.toArray(new ResourceType[types.size()]);
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable PropertiesContainer container,
                                 @Nullable PropertyContext context,
                                 Object object,
                                 @Nullable InplaceContext inplaceContext) {
    myComponent = (RadComponent)container;
    myRootComponent = context instanceof RadPropertyContext ? ((RadPropertyContext)context).getRootComponent() : null;

    String value = (String)object;
    JTextField text = getComboText();

    if (text == null) {
      if (StringUtil.isEmpty(value) || value.equals("true") || value.equals("false")) {
        myBooleanResourceValue = null;
      }
      else {
        myBooleanResourceValue = value;
      }

      try {
        myIgnoreCheckBoxValue = true;
        myCheckBox.setSelected(Boolean.parseBoolean(value));
      }
      finally {
        myIgnoreCheckBoxValue = false;
      }

      if (inplaceContext == null) {
        myEditor.setBorder(null);
        myCheckBox.setText(null);
      }
      else {
        myEditor.setBorder(myCheckBoxBorder);
        myCheckBox.setText(myBooleanResourceValue);
      }
    }
    else {
      text.setText(value);
      if (inplaceContext != null) {
        text.setColumns(0);
        if (inplaceContext.isStartChar()) {
          text.setText(inplaceContext.getText(text.getText()));
        }
      }
    }
    return myEditor;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    JTextField text = getComboText();
    return text == null ? myCheckBox : text;
  }

  @Override
  public Object getValue() {
    JTextField text = getComboText();
    if (text == null) {
      return myBooleanResourceValue == null ? Boolean.toString(myCheckBox.isSelected()) : myBooleanResourceValue;
    }
    String value = text.getText();
    if (value == StringsComboEditor.UNSET || StringUtil.isEmpty(value)) {
      return null;
    }
    if (myIsDimension &&
        !value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) &&
        !value.startsWith(SdkConstants.PREFIX_THEME_REF) &&
        !value.endsWith(SdkConstants.UNIT_DIP) &&
        !value.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT) &&
        !value.equalsIgnoreCase(SdkConstants.VALUE_FILL_PARENT) &&
        !value.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
      if (value.length() <= 2) {
        return value + SdkConstants.UNIT_DP;
      }
      int index = value.length() - 2;
      String dimension = value.substring(index);
      if (ArrayUtil.indexOf(ResourceRenderer.DIMENSIONS, dimension) == -1) {
        return value + SdkConstants.UNIT_DP;
      }
    }

    // If it looks like a reference, don't escape it.
    if (myIsString &&
        (value.startsWith(SdkConstants.PREFIX_RESOURCE_REF) || value.startsWith(SdkConstants.PREFIX_THEME_REF)) &&
        ResourceUrl.parse(value) == null) {
      return "\\" + value;
    }
    return value;
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myEditor);
  }

  protected void showDialog() {
    Module module = RadModelBuilder.getModule(myRootComponent);
    if (module == null) {
      if (myBooleanResourceValue != null) {
        fireEditingCancelled();
      }
      return;
    }
    if (DumbService.isDumb(module.getProject())) {
      DumbService.getInstance(module.getProject()).showDumbModeNotification("Resources are not available during indexing");
      return;
    }

    ResourceDialog dialog = new ResourceDialog(module, myTypes, (String)getValue(), (RadViewComponent)myComponent);
    if (dialog.showAndGet()) {
      setValue(dialog.getResourceName());
    }
    else {
      if (myBooleanResourceValue != null) {
        fireEditingCancelled();
      }
    }
  }

  protected final void setValue(String value) {
    JTextField text = getComboText();
    if (text == null) {
      myBooleanResourceValue = value;
      fireValueCommitted(false, true);
    }
    else {
      text.setText(value);
      fireValueCommitted(true, true);
    }
  }

  private JTextField getComboText() {
    JComponent component = myEditor.getChildComponent();
    if (component instanceof JTextField) {
      return (JTextField)component;
    }
    if (component instanceof JComboBox) {
      JComboBox combo = (JComboBox)component;
      return (JTextField)combo.getEditor().getEditorComponent();
    }
    return null;
  }

  private static final class MyComboBox extends ComboBox {
    public MyComboBox() {
      ((JTextField)getEditor().getEditorComponent()).addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (isPopupVisible()) {
            ComboPopup popup = getPopup();
            if (popup != null) {
              setSelectedItem(popup.getList().getSelectedValue());
            }
          }
        }
      });
    }
  }
}