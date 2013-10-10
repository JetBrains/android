/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.intentions;

import com.android.resources.ResourceType;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.jetbrains.android.util.AndroidUtils.VIEW_CLASS_NAME;

public class AndroidExtractDimensionAction extends AndroidAddStringResourceAction {
  @Override
  @NotNull
  public String getText() {
    return AndroidBundle.message("extract.dimension.intention.text");
  }

  @Override
  protected ResourceType getType() {
    return ResourceType.DIMEN;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    // We don't support extracting dimensions in Java files yet. Unlike strings, the
    // presence of a number doesn't necessarily imply that it's a dimension -- it could
    // be a color integer, it could be a loop index, it could be a bitmask, ... And
    // even if it's a dimension, it's not clear how we would convert it from a pixel
    // (which is used in most APIs) to a dimension, since we shouldn't put px resources
    // into resource files; they should be dips, but of course the pixel to dp depends
    // on the screen dpi.
    if (file.getFileType() == StdFileTypes.XML) {
      AndroidFacet facet = AndroidFacet.getInstance(file);
      if (facet != null) {
        PsiElement element = getPsiElement(file, editor);
        if (element != null) {
          String value = getStringLiteralValue(project, element, file, getType());
          if (value != null && !value.isEmpty()) {
            // Only allow conversions on numbers (e.g. "50px") such that we don't
            // offer to extract @dimen/foo or wrap_content, and also only allow
            // conversions on dimensions (numbers followed by a unit) so we don't
            // for example offer to extract an integer into a dimension
            if (Character.isDigit(value.charAt(0)) && !Character.isDigit(value.charAt(value.length() - 1))) {
              // Only allow conversions on dimensions (numbers with units)
              return true;
            }
          }
        }
      }
    }

    return false;
  }
}
