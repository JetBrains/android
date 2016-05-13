/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
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
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class NlIdPropertyItem extends NlPropertyItem {
  private static final int REFACTOR_ASK = 0;
  private static final int REFACTOR_NO = 1;
  private static final int REFACTOR_YES = 2;

  private static int ourRefactoringChoice = REFACTOR_ASK;

  protected NlIdPropertyItem(@NotNull List<NlComponent> components,
                             @NotNull XmlAttributeDescriptor descriptor,
                             @Nullable AttributeDefinition attributeDefinition) {
    super(components, descriptor, SdkConstants.ANDROID_URI, attributeDefinition);
  }

  @Override
  public void setValue(Object value) {
    String newId = value != null ? value.toString() : "";
    String oldId = getValue();
    XmlTag tag = getTag();

    if (ourRefactoringChoice != REFACTOR_NO
        && oldId != null
        && !oldId.isEmpty()
        && !newId.isEmpty()
        && !oldId.equals(newId)
        && tag != null
        && tag.isValid()) {
      // Offer rename refactoring?
      XmlAttribute attribute = tag.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI);
      if (attribute != null) {
        Module module = getModel().getModule();
        Project project = module.getProject();
        XmlAttributeValue valueElement = attribute.getValueElement();
        if (valueElement != null && valueElement.isValid()) {
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

              builder.addActionDescriptor(dialogWrapper -> new AbstractAction(Messages.NO_BUTTON) {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                  dialogWrapper.close(DialogWrapper.NEXT_USER_EXIT_CODE);
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
    super.setValue(value);
  }
}
