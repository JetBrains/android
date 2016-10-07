package org.jetbrains.android.dom;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCreateOnClickHandlerAction extends AbstractIntentionAction implements HighPriorityAction {
  @NotNull
  @Override
  public String getText() {
    return AndroidBundle.message("create.on.click.handler.intention.text");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (editor == null || !(file instanceof XmlFile)) {
      return false;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(file);

    if (facet == null) {
      return false;
    }
    final XmlAttributeValue attrValue = getXmlAttributeValue(file, editor);

    if (attrValue == null) {
      return false;
    }
    final PsiElement parent = attrValue.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return false;
    }
    final GenericAttributeValue domValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)parent);

    if (domValue == null || !(domValue.getConverter() instanceof OnClickConverter)) {
      return false;
    }
    final String methodName = attrValue.getValue();
    return methodName != null && StringUtil.isJavaIdentifier(methodName);
  }

  @Nullable
  private static XmlAttributeValue getXmlAttributeValue(PsiFile file, Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    return element != null ? PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) : null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;

    final XmlAttributeValue attrValue = getXmlAttributeValue(file, editor);
    assert attrValue != null;
    final String methodName = attrValue.getValue();
    assert methodName != null;
    final GenericAttributeValue domValue = DomManager.getDomManager(project).getDomElement((XmlAttribute)attrValue.getParent());
    assert domValue != null;
    final OnClickConverter converter = (OnClickConverter)domValue.getConverter();

    final PsiClass activityBaseClass = JavaPsiFacade.getInstance(project).findClass(
      AndroidUtils.ACTIVITY_BASE_CLASS_NAME, facet.getModule().getModuleWithDependenciesAndLibrariesScope(false));

    if (activityBaseClass == null) {
      return;
    }
    final GlobalSearchScope scope = facet.getModule().getModuleScope(false);
    final PsiClass selectedClass;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final Ref<PsiClass> selClassRef = Ref.create();

      ClassInheritorsSearch.search(activityBaseClass, scope, true, true, false).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass psiClass) {
          if (!psiClass.isInterface() && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            selClassRef.set(psiClass);
            return false;
          }
          return true;
        }
      });
      selectedClass = selClassRef.get();
    }
    else {
      final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).createInheritanceClassChooser(
        "Choose Activity to Create the Method", scope, activityBaseClass, null, new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return !converter.findHandlerMethod(aClass, methodName);
        }
      });
      chooser.showDialog();
      selectedClass = chooser.getSelected();
    }

    if (selectedClass != null) {
      addHandlerMethodAndNavigate(project, selectedClass, methodName, converter.getDefaultMethodParameterType(selectedClass));
    }
  }

  @NotNull
  private static String suggestVarName(@NotNull String type) {
    for (int i = type.length() - 1; i >= 0; i--) {
      final char c = type.charAt(i);

      if (Character.isUpperCase(c)) {
        return type.substring(i).toLowerCase(Locale.US);
      }
    }
    return "o";
  }

  @Nullable
  public static PsiMethod addHandlerMethod(@NotNull Project project,
                                           @NotNull PsiClass psiClass,
                                           @NotNull String methodName,
                                           @NotNull String methodParamType) {
    final PsiFile file = psiClass.getContainingFile();

    if (file == null || !FileModificationService.getInstance().prepareFileForWrite(file)) {
      return null;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String varName = suggestVarName(methodParamType);
    PsiMethod method = (PsiMethod)psiClass.add(factory.createMethodFromText(
      "public void " + methodName + "(" + methodParamType + " " + varName + ") {}", psiClass));

    PsiMethod method1 = (PsiMethod)CodeStyleManager.getInstance(project).reformat(method);
    method1 = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(method1);
    return (PsiMethod)method.replace(method1);
  }

  public static void addHandlerMethodAndNavigate(@NotNull final Project project,
                                                 @NotNull final PsiClass psiClass,
                                                 @NotNull final String methodName,
                                                 @NotNull final String methodParamType) {
    if (!AndroidUtils.isIdentifier(methodName)) {
      Messages.showErrorDialog(project, String.format("%1$s is not a valid Java identifier/method name.", methodName), "Invalid Name");
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final PsiMethod method = addHandlerMethod(project, psiClass, methodName, methodParamType);

        if (method == null) {
          return;
        }
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          PsiNavigateUtil.navigate(method);
        }
        final PsiFile javaFile = method.getContainingFile();

        if (javaFile == null) {
          return;
        }
        final Editor javaEditor = PsiUtilBase.findEditor(method);

        if (javaEditor == null) {
          return;
        }
        final PsiCodeBlock body = method.getBody();

        if (body != null) {
          final PsiJavaToken lBrace = body.getLBrace();

          if (lBrace != null) {
            javaEditor.getCaretModel().moveToOffset(lBrace.getTextRange().getEndOffset());
          }
        }
      }
    });
  }
}
