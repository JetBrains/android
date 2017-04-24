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

package com.android.tools.idea.actions;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.databinding.DataBindingProjectComponent;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

/**
 * Converts layout to a data binding layout
 * <p>
 * TODO:
 * <ul>
 *   <li> Add variable declarations? Maybe reuse the change signature refactoring UI to let users declare variables and types</li>
 *   <li> Add a binding variable to the activities? (Maybe pick activity from the tools:context or other layout references) </li>
 *   <li> Actually, maybe refactor activity code completely to call set bindings etc - note that list item usage is typically
 *        different from normal activity layout usage </li>
 *   <li> Have lint warning for referencing a layout directly (setContentLayout) when that layout is using data binding? </li>
 * </ul>
 */
public class ConvertLayoutToDataBindingAction extends AbstractIntentionAction implements HighPriorityAction {

  @Override
  @NotNull
  public String getText() {
    return "Convert to data binding layout";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (ResourceHelper.getFolderType(file) != ResourceFolderType.LAYOUT) {
      return false;
    }
    if (!(file instanceof XmlFile)) {
      return false;
    }

    XmlFile xmlFile = (XmlFile)file;
    XmlTag tag = xmlFile.getRootTag();
    if (tag == null) {
      return false;
    }

    // Enable this action on the root element of layout files
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return false;
    }

    if (!tag.equals(element.getParent()) && offset >= tag.getTextOffset()) {
      // Only allow selection on the root tag (or in the whitespace/comments/prolog before the root
      return false;
    }

    if (tag.getName().equals(TAG_LAYOUT)) {
      // Already a data binding layout
      return false;
    }

    return isUsingDataBinding(project);
  }

  protected boolean isUsingDataBinding(@NotNull Project project) {
    DataBindingProjectComponent component = project.getComponent(DataBindingProjectComponent.class);
    return component != null && component.hasAnyDataBindingEnabledFacet();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    XmlFile xmlFile = (XmlFile) file;
    XmlTag root = xmlFile.getRootTag();
    assert root != null;

    XmlElementFactory factory = XmlElementFactory.getInstance(project);
    XmlTag newRoot = factory.createTagFromText("<layout>\n<data>\n\n</data></layout>");
    String rootText = root.getText();
    newRoot = (XmlTag)root.replace(newRoot);
    // Reparse the XML; just doing newRoot.addSubtag(root) doesn't work
    root = factory.createTagFromText(rootText);

    // Transfer namespace elements
    for (XmlAttribute attribute : root.getAttributes()) {
      String name = attribute.getName();
      if (name.startsWith(XMLNS_PREFIX)) {
        newRoot.setAttribute(name, attribute.getValue());
        attribute.delete();
      }
    }

    // Transfer tools:context
    XmlAttribute context = root.getAttribute(ATTR_CONTEXT, TOOLS_URI);
    if (context != null) {
      root.setAttribute(ATTR_CONTEXT, TOOLS_URI, context.getValue());
      context.delete();
    }

    newRoot.addSubTag(root, false);
  }
}
