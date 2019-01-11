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
package com.android.tools.idea.uibuilder.property;

import static com.android.SdkConstants.ANDROID_ID_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.xml.XmlName;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class NlIdPropertyItem extends NlPropertyItem {

  @VisibleForTesting
  static final int NO_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE;
  @VisibleForTesting
  static final int PREVIEW_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 1;

  private static final int REFACTOR_ASK = 0;
  private static final int REFACTOR_NO = 1;
  private static final int REFACTOR_YES = 2;
  private static final int REFACTOR_PREVIEW = 3;

  // TODO move this static field to a PropertiesComponent setting (need a UI to reset)
  private static int ourRefactoringChoice = REFACTOR_ASK;

  private Supplier<? extends DialogBuilder> myDialogSupplier;
  private Supplier<? extends RenameProcessor> myRenameProcessorSupplier;

  protected NlIdPropertyItem(@NotNull XmlName name,
                             @Nullable AttributeDefinition attributeDefinition,
                             @NotNull List<NlComponent> components,
                             @Nullable PropertiesManager propertiesManager) {
    super(name, attributeDefinition, components, propertiesManager);
  }

  @Nullable
  @Override
  public String getValue() {
    return stripIdPrefix(super.getValue());
  }

  /**
   * Like {@link com.android.tools.lint.detector.api.LintUtils#stripIdPrefix(String)} but doesn't return "" for a null id
   */
  private static String stripIdPrefix(@Nullable String id) {
    if (id != null) {
      if (id.startsWith(NEW_ID_PREFIX)) {
        return id.substring(NEW_ID_PREFIX.length());
      }
      else if (id.startsWith(ID_PREFIX)) {
        return id.substring(ID_PREFIX.length());
      }
    }
    return id;
  }

  @Nullable
  @Override
  public String resolveValue(@Nullable String value) {
    return value;
  }

  @Override
  public void setValue(Object value) {
    String newId = value != null ? stripIdPrefix(value.toString()) : "";
    String oldId = getValue();
    XmlTag tag = getTag();
    String newValue = !StringUtil.isEmpty(newId) && !newId.startsWith(ANDROID_ID_PREFIX) ? NEW_ID_PREFIX + newId : newId;

    if (ourRefactoringChoice != REFACTOR_NO
        && oldId != null
        && !oldId.isEmpty()
        && !newId.isEmpty()
        && newValue != null
        && !oldId.equals(newId)
        && tag != null
        && tag.isValid()) {
      // Offer rename refactoring?
      XmlAttribute attribute = tag.getAttribute(ATTR_ID, ANDROID_URI);
      if (attribute != null) {
        Module module = getModel().getModule();
        Project project = module.getProject();
        XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null && valueElement.isValid()) {
          // Exact replace only, no comment/text occurrence changes since it is non-interactive
          ValueResourceElementWrapper wrapper = new ValueResourceElementWrapper(valueElement);
          RenameProcessor processor = createRenameProcessor(project, wrapper, newValue);
          // Do a quick usage search to see if we need to ask about renaming
          UsageInfo[] usages = processor.findUsages();
          if (usages.length > 0) {
            int choice = ourRefactoringChoice;
            if (choice == REFACTOR_ASK) {
              DialogBuilder builder = createDialogBuilder(project);
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

              builder.addActionDescriptor(dialogWrapper -> new AbstractAction(Messages.NO_BUTTON) {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                  dialogWrapper.close(NO_EXIT_CODE);
                }
              });

              builder.addActionDescriptor(dialogWrapper -> new AbstractAction("Preview") {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                  dialogWrapper.close(PREVIEW_EXIT_CODE);
                }
              });

              builder.addCancelAction();
              int exitCode = builder.show();

              choice = exitCode == DialogWrapper.OK_EXIT_CODE ? REFACTOR_YES :
                       exitCode == NO_EXIT_CODE ? REFACTOR_NO :
                       exitCode == PREVIEW_EXIT_CODE ? REFACTOR_PREVIEW : ourRefactoringChoice;

              //noinspection AssignmentToStaticFieldFromInstanceMethod
              ourRefactoringChoice = checkBox.isSelected() ? choice : REFACTOR_ASK;

              if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
                return;
              }
            }

            if (choice == REFACTOR_YES) {
              processor.setPreviewUsages(false);
              processor.run();
              return;
            }
            else if (choice == PREVIEW_EXIT_CODE) {
              processor.setPreviewUsages(true);
              processor.run();
              return;
            }
          }
        }
      }
    }

    super.setValue(newValue);
  }

  @TestOnly
  void setDialogSupplier(@NotNull Supplier<? extends DialogBuilder> dialogSupplier) {
    myDialogSupplier = dialogSupplier;
  }

  @TestOnly
  static void clearRefactoringChoice() {
    ourRefactoringChoice = REFACTOR_ASK;
  }

  private DialogBuilder createDialogBuilder(@NotNull Project project) {
    return myDialogSupplier != null ? myDialogSupplier.get() : new DialogBuilder(project);
  }

  @TestOnly
  void setRenameProcessSupplier(@NotNull Supplier<? extends RenameProcessor> renameProcessorSupplier) {
    myRenameProcessorSupplier = renameProcessorSupplier;
  }

  private RenameProcessor createRenameProcessor(@NotNull Project project,
                                                @NotNull ValueResourceElementWrapper wrapper,
                                                @NotNull String newValue) {
    return myRenameProcessorSupplier != null
           ? myRenameProcessorSupplier.get()
           : new RenameProcessor(project, wrapper, newValue, false, false);
  }
}
