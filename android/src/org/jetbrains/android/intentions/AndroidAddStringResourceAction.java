/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.intentions;

import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.AnnotationUtil;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.VIEW_CLASS_NAME;

public class AndroidAddStringResourceAction extends AbstractIntentionAction implements HighPriorityAction {

  @Override
  @NotNull
  public String getText() {
    return AndroidBundle.message("add.string.resource.intention.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return false;
    }
    PsiElement element = getPsiElement(file, editor);
    return element != null && getStringLiteralValue(project, element, file, getType()) != null;
  }

  protected ResourceType getType() {
    return ResourceType.STRING;
  }

  @Nullable
  protected static String getStringLiteralValue(@NotNull Project project, @NotNull PsiElement element, @NotNull PsiFile file,
                                                ResourceType resourceType) {
    if (file instanceof PsiJavaFile && element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      Object value = literalExpression.getValue();
      if (resourceType == ResourceType.STRING && value instanceof String) {
        return (String)value;
      } else if (resourceType == ResourceType.DIMEN && (value instanceof Integer || value instanceof Float)) {
        return value.toString();
      }
    }
    else if (file instanceof XmlFile && element instanceof XmlAttributeValue) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);

      if (attribute != null) {
        final GenericAttributeValue domAttribute = DomManager.getDomManager(project).getDomElement(attribute);

        if (domAttribute != null) {
          final Converter converter = domAttribute.getConverter();

          if (converter instanceof ResourceReferenceConverter) {
            final ResourceValue value = (ResourceValue)domAttribute.getValue();

            if (value != null && !value.isReference()) {
              final Set<String> types = ((ResourceReferenceConverter)converter).getResourceTypes(domAttribute);

              String typeName = resourceType.getName();
              for (String type : types) {
                if (typeName.equals(type)) {
                  // This returns the XML attribute text, except for the surrounding quotes
                  String attributeText = ((XmlAttributeValue)element).getValue();
                  if (attributeText != null) {
                    // We want to turn &quot; etc back into " in the XML string definition; the entity
                    // usage was just to escape XML, not a part of the text
                    return ValueXmlHelper.unescapeResourceString(attributeText, true, true);
                  }
                  return null;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass getContainingInheritorOf(@NotNull PsiElement element, @NotNull String... baseClassNames) {
    PsiClass c = null;
    do {
      c = PsiTreeUtil.getParentOfType(c == null ? element : c, PsiClass.class);
      for (String name : baseClassNames) {
        if (InheritanceUtil.isInheritor(c, name)) {
          return c;
        }
      }
    }
    while (c != null);
    return null;
  }

  @Nullable
  protected static PsiElement getPsiElement(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return element != null ? element.getParent() : null;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doInvoke(project, editor, file, null, getType());
  }

  static void doInvoke(Project project, Editor editor, PsiFile file, @Nullable String resName, ResourceType type) {
    final PsiElement element = getPsiElement(file, editor);
    assert element != null;

    doInvoke(project, editor, file, resName, element, type);
  }

  protected static void doInvoke(Project project, Editor editor, PsiFile file, @Nullable String resName, PsiElement element,
                                 ResourceType type) {
    String value = getStringLiteralValue(project, element, file, type);
    assert value != null;

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;

    final String aPackage = getPackage(facet);
    if (aPackage == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("package.not.found.error"), CommonBundle.getErrorTitle());
      return;
    }

    if (resName != null && ApplicationManager.getApplication().isUnitTestMode()) {
      String fileName = AndroidResourceUtil.getDefaultResourceFileName(type);
      assert fileName != null;
      AndroidResourceUtil.createValueResource(facet.getModule(), resName, type, fileName,
                                              Collections.singletonList(ResourceFolderType.VALUES.getName()), value);
    }
    else {
      Module facetModule = facet.getModule();
      boolean chooseName = resName != null || ResourceHelper.prependResourcePrefix(facetModule, null) != null;
      final CreateXmlResourceDialog dialog = new CreateXmlResourceDialog(facetModule, type, resName, value, chooseName);
      dialog.setTitle("Extract Resource");
      if (!dialog.showAndGet()) {
        return;
      }

      final Module module = dialog.getModule();
      if (module == null) {
        return;
      }

      resName = dialog.getResourceName();
      if (!AndroidResourceUtil
        .createValueResource(module, resName, type, dialog.getFileName(), dialog.getDirNames(), value)) {
        return;
      }
    }

    if (file instanceof PsiJavaFile) {
      createJavaResourceReference(facet.getModule(), editor, file, element, aPackage, resName, type);
    }
    else {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
      if (attribute != null) {
        attribute.setValue(ResourceValue.referenceTo('@', null, type.getName(), resName).toString());
      }
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    UndoUtil.markPsiFileForUndo(file);
    AndroidLayoutPreviewToolWindowManager.renderIfApplicable(project);
  }

  private static final String STRING_RES_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "StringRes";

  private static boolean hasMethodOnlyOverloadedWithOneIntParameter(final PsiMethod method, int index) {
    if (method.getNameIdentifier() == null) {
      return false;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final int parameterCount = parameterList.getParametersCount();

    if (parameterCount == 0) {
      return false;
    }

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }

    final String methodName = method.getName();
    final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, false);
    for (PsiMethod sameNameMethod : sameNameMethods) {
      if (method.equals(sameNameMethod)) {
        continue;
      }
      final PsiParameterList otherParameterList = sameNameMethod.getParameterList();
      if (parameterCount != otherParameterList.getParametersCount()) {
        continue;
      }

      boolean found = true;
      for (int i = 0; i < parameterCount; i++) {
        PsiParameter parameter = parameterList.getParameters()[i];
        PsiParameter otherParameter = otherParameterList.getParameters()[i];

        // We want to find a method that all parameters matches except ith parameter be int.
        if (i == index) {
          if (!PsiType.INT.equals(otherParameter.getType())) {
            found = false;
            break;
          } else {
            if (!AnnotationUtil.isAnnotated(otherParameter, STRING_RES_ANNOTATION, false, false)) {
              found = false;
              break;
            }
          }
        }
        else if (!parameter.getType().equals(otherParameter.getType())) {
          found = false;
          break;
        }
      }

      if (found) {
        return true;
      }
    }
    return false;
  }

  private static boolean useGetStringMethodForStringRes(final PsiElement element) {
    // Check if the element is an argument of a method call.
    if (element.getParent() instanceof PsiExpressionList) {
      PsiExpressionList expressionList = (PsiExpressionList)element.getParent();
      int index = Arrays.asList(expressionList.getExpressions()).indexOf(element);

      PsiElement prevSibling = expressionList.getPrevSibling();
      if (prevSibling != null && prevSibling.getReference() != null) {
        PsiElement resolved = prevSibling.getReference().resolve();
        if (resolved instanceof PsiMethod && hasMethodOnlyOverloadedWithOneIntParameter((PsiMethod)resolved, index)) {
          return false;
        }
      }
    }
    return true;
  }

  private static void createJavaResourceReference(final Module module,
                                                  final Editor editor,
                                                  final PsiFile file,
                                                  final PsiElement element,
                                                  final String aPackage,
                                                  final String resName,
                                                  final ResourceType resType) {
    final boolean extendsContext = getContainingInheritorOf(element, CLASS_CONTEXT) != null;
    final String rJavaFieldName = AndroidResourceUtil.getRJavaFieldName(resName);
    final String field = aPackage + ".R." + resType + '.' + rJavaFieldName;
    final String methodName = getGetterNameForResourceType(resType, element);
    assert methodName != null;
    final TemplateImpl template;
    final boolean inStaticContext = RefactoringUtil.isInStaticContext(element, null);
    final Project project = module.getProject();

    if (extendsContext && !inStaticContext) {
      if (ResourceType.STRING == resType) {
        if (useGetStringMethodForStringRes(element)) {
          template = new TemplateImpl("", methodName + '(' + field + ')', "");
        } else {
          template = new TemplateImpl("", field, "");
        }
      }
      else {
        template = new TemplateImpl("", "$resources$." + methodName + "(" + field + ")", "");
        MacroCallNode node = new MacroCallNode(new MyVarOfTypeExpression("getResources()"));
        node.addParameter(new ConstantNode(CLASS_RESOURCES));
        template.addVariable("resources", node, new ConstantNode(""), true);
      }
    }
    else {
      boolean addContextVariable = true;
      if (ResourceType.STRING == resType) {
        if (useGetStringMethodForStringRes(element)) {
          template = new TemplateImpl("", "$context$." + methodName + '(' + field + ')', "");
        } else {
          template = new TemplateImpl("", field, "");
          addContextVariable = false;
        }
      }
      else {
        template = new TemplateImpl("", "$context$.getResources()." + methodName + "(" + field + ")", "");
      }
      if (addContextVariable) {
        final boolean extendsView = getContainingInheritorOf(element, VIEW_CLASS_NAME) != null;
        MacroCallNode node = new MacroCallNode(extendsView && !inStaticContext ? new MyVarOfTypeExpression("getContext()") : new VariableOfTypeMacro());
        node.addParameter(new ConstantNode(CLASS_CONTEXT));
        template.addVariable("context", node, new ConstantNode(""), true);
      }
    }
    final int offset = element.getTextOffset();
    editor.getCaretModel().moveToOffset(offset);
    final TextRange elementRange = element.getTextRange();
    editor.getDocument().deleteString(elementRange.getStartOffset(), elementRange.getEndOffset());
    final RangeMarker marker = editor.getDocument().createRangeMarker(offset, offset);
    marker.setGreedyToLeft(true);
    marker.setGreedyToRight(true);
    //noinspection ConstantConditions
    TemplateManager.getInstance(project).startTemplate(editor, template, false, null, new TemplateEditingAdapter() {
      @Override
      public void waitingForInput(Template template) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, marker.getStartOffset(), marker.getEndOffset());
      }

      @Override
      public void beforeTemplateFinished(TemplateState state, Template template) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file, marker.getStartOffset(), marker.getEndOffset());
      }
    });
  }

  @Nullable
  private static String getPackage(@NotNull AndroidFacet facet) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) return null;
    return manifest.getPackage().getValue();
  }

  @Nullable
  private static String getGetterNameForResourceType(@NotNull ResourceType resourceType, @NotNull PsiElement element) {
    String type = resourceType.getName();
    if (type.length() < 2) return null;
    if (resourceType == ResourceType.DIMEN) {
      // Choose between getDimensionPixelSize and getDimension based on whether we're needing an int or a float
      PsiType targetType = computeTargetType(element);
      if (targetType != null && PsiType.INT.equals(targetType)) {
        return "getDimensionPixelSize";
      }
      return "getDimension";
    }
    return "get" + Character.toUpperCase(type.charAt(0)) + type.substring(1);
  }

  @Nullable
  private static PsiType computeTargetType(PsiElement element) {
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (call != null) {
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(element, PsiExpressionList.class, true);
      if (expressionList != null) {
        int index = ArrayUtil.indexOf(expressionList.getExpressions(), element);
        if (index >= 0) {
          PsiMethod resolved = call.resolveMethod();
          if (resolved != null) {
            PsiParameterList parameterList = resolved.getParameterList();
            if (index >= 0 && index < parameterList.getParametersCount()) {
              PsiParameter psiParameter = parameterList.getParameters()[index];
              return psiParameter.getType();
            }
          }
        }
      }
    }
    else {
      PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class, false);
      if (variable != null) {
        return variable.getType();
      }
    }

    return null;
  }


  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static class MyVarOfTypeExpression extends VariableOfTypeMacro {
    private final String myDefaultValue;

    private MyVarOfTypeExpression(@NotNull String defaultValue) {
      myDefaultValue = defaultValue;
    }

    @Override
    public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
      return new TextResult(myDefaultValue);
    }

    @Override
    public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
      return new TextResult(myDefaultValue);
    }

    @Override
    public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
      final PsiElement[] vars = getVariables(params, context);
      if (vars == null || vars.length == 0) {
        return null;
      }
      final Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      for (PsiElement var : vars) {
        JavaTemplateUtil.addElementLookupItem(set, var);
      }
      LookupElement[] elements = set.toArray(new LookupElement[set.size()]);
      if (elements.length == 0) {
        return elements;
      }
      LookupElement lookupElementForDefValue = LookupElementBuilder.create(myDefaultValue);
      LookupElement[] result = new LookupElement[elements.length + 1];
      result[0] = lookupElementForDefValue;
      System.arraycopy(elements, 0, result, 1, elements.length);
      return result;
    }
  }
}
