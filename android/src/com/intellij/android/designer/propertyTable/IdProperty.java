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
package com.intellij.android.designer.propertyTable;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.android.designer.model.IdManager;
import com.intellij.android.designer.model.RadModelBuilder;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.InplaceContext;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Customized renderer and property editors for the ID property
 */
public class IdProperty extends AttributeProperty {
  private static final int REFACTOR_ASK = 0;
  private static final int REFACTOR_NO = 1;
  private static final int REFACTOR_YES = 2;
  private static int ourRefactoringChoice = REFACTOR_ASK;

  public static final Property INSTANCE = new IdProperty();

  private IdProperty() {
    this(ATTR_ID, new AttributeDefinition(ATTR_ID, null, Collections.singletonList(AttributeFormat.Reference)));
    setImportant(true);
  }

  public IdProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    super(name, definition);
  }

  public IdProperty(@Nullable Property parent, @NotNull String name, @NotNull AttributeDefinition definition) {
    super(parent, name, definition);
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new IdProperty(parent, name, myDefinition);
  }

  @Override
  public void setValue(@NotNull final RadViewComponent component, final Object value) throws Exception {
    final String newId = value != null ? value.toString() : "";

    final String oldId = component.getId();

    if (ourRefactoringChoice != REFACTOR_NO
        && oldId != null
        && !oldId.isEmpty()
        && !newId.isEmpty()
        && !oldId.equals(newId)
        && component.getTag().isValid()) {
      // Offer rename refactoring?
      XmlTag tag = component.getTag();
      XmlAttribute attribute = tag.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI);
      if (attribute != null) {
        Module module = RadModelBuilder.getModule(component);
        if (module != null) {
          XmlAttributeValue valueElement = attribute.getValueElement();
          if (valueElement != null && valueElement.isValid()) {
            final Project project = module.getProject();
            // Exact replace only, no comment/text occurrence changes since it is non-interactive
            RenameProcessor processor = new RenameProcessor(project, valueElement, newId, false /*comments*/, false /*text*/);
            processor.setPreviewUsages(false);
            // Do a quick usage search to see if we need to ask about renaming
            UsageInfo[] usages = processor.findUsages();
            if (usages.length > 0) {
              int choice = ourRefactoringChoice;
              if (choice == REFACTOR_ASK) {
                DialogBuilder builder = new DialogBuilder(project);
                builder.setTitle("Update Usages?");
                JPanel panel = new JPanel(new BorderLayout()); // UGH!
                JLabel label = new JLabel("<html>" +
                                          "Update usages as well?<br>" +
                                          "This will update all XML references and Java R field references.<br>" +
                                          "<br>" +
                                          "</html>");
                panel.add(label, BorderLayout.CENTER);
                JBCheckBox checkBox = new JBCheckBox("Don't ask again during this session");
                panel.add(checkBox, BorderLayout.SOUTH);
                builder.setCenterPanel(panel);
                builder.setDimensionServiceKey("idPropertyDimension");
                builder.removeAllActions();

                DialogBuilder.CustomizableAction yesAction = builder.addOkAction();
                yesAction.setText(Messages.YES_BUTTON);

                builder.addActionDescriptor(new DialogBuilder.ActionDescriptor() {
                  @Override
                  public Action getAction(final DialogWrapper dialogWrapper) {
                    return new AbstractAction(Messages.NO_BUTTON) {
                      @Override
                      public void actionPerformed(ActionEvent actionEvent) {
                        dialogWrapper.close(DialogWrapper.NEXT_USER_EXIT_CODE);
                      }
                    };
                  }
                });
                builder.addCancelAction();
                int exitCode = builder.show();

                choice = exitCode == DialogWrapper.OK_EXIT_CODE ? REFACTOR_YES :
                             exitCode == DialogWrapper.NEXT_USER_EXIT_CODE ? REFACTOR_NO : ourRefactoringChoice;

                //noinspection AssignmentToStaticFieldFromInstanceMethod
                ourRefactoringChoice = checkBox.isSelected() ? choice : REFACTOR_ASK;

                if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
                  return;
                }
              }

              if (choice == REFACTOR_YES) {
                processor.run();
                // Fall through to also set the value in the layout editor property; otherwise we'll be out of sync
              }
            }
          }
        }
      }
    }

    super.setValue(component, value);
  }

  @Override
  public boolean availableFor(List<PropertiesContainer> components) {
    return false;
  }

  private final PropertyRenderer myRenderer = new IdPropertyRenderer();
  private final IdPropertyEditor myEditor = new IdPropertyEditor();

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return myEditor;
  }

  /**
   * Customized such that we can filter out the @+id/@id prefixes
   * <p>
   * Also, no need to include a "..." button; you pretty much never want to link your declaration to an existing id
   */
  private static class IdPropertyRenderer extends LabelPropertyRenderer {
    public IdPropertyRenderer() {
      super(null);
    }

    // Strip the @id/@+id/ prefix
    @Override
    @NotNull
    public JComponent getComponent(@Nullable PropertiesContainer container,
                                   PropertyContext context,
                                   @Nullable Object value,
                                   boolean selected,
                                   boolean hasFocus) {
      String s = value != null ? value.toString() : "";
      value = IdManager.getIdName(s);
      return super.getComponent(container, context, value, selected, hasFocus);
    }
  }

  /**
   * Customized such that we can filter out (and on edit, put back) the @+id/@id prefixes
   * <p>
   * Also, no need to include a "..." button; you pretty much never want to link your declaration to an existing id
   */
  private static class IdPropertyEditor extends PropertyEditor {
    private final JTextField myEditor = new JTextField();

    private IdPropertyEditor() {
      JTextField textField = myEditor;
      textField.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          fireValueCommitted(false, true);
        }
      });
      ResourceEditor.selectTextOnFocusGain(textField);
    }

    @NotNull
    @Override
    public JComponent getComponent(@Nullable PropertiesContainer container,
                                   @Nullable PropertyContext context,
                                   Object value,
                                   @Nullable InplaceContext inplaceContext) {
      myEditor.setText(value != null ? IdManager.getIdName(value.toString()) : "");
      preferredSizeChanged();
      return myEditor;
    }

    @Nullable
    @Override
    public Object getValue() throws Exception {
      String text = myEditor.getText().trim();
      if (text.isEmpty()) {
        return text;
      }
      if (!text.startsWith(PREFIX_RESOURCE_REF)) {
        text = NEW_ID_PREFIX + text;
      }

      String name = LintUtils.stripIdPrefix(text);
      if (name.length() > 0) {
        ResourceNameValidator validator = ResourceNameValidator.create(false, (Set<String>)null, ResourceType.ID);
        String errorText = validator.getErrorText(name);
        if (errorText != null) {
          throw new IllegalArgumentException(errorText);
        }
      }

      return text;
    }

    @Override
    public void updateUI() {
      SwingUtilities.updateComponentTreeUI(myEditor);
    }
  }
}