package org.jetbrains.android.dom.converters;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.AndroidGotoRelatedProvider;
import org.jetbrains.android.dom.AndroidCreateOnClickHandlerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class OnClickConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final int length = element.getTextLength();
    if (length > 1) {
      return new PsiReference[]{new MyReference((XmlAttributeValue)element, new TextRange(1, length - 1))};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  private static class MyReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> implements LocalQuickFixProvider {

    public MyReference(XmlAttributeValue value, TextRange range) {
      super(value, range, true);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return ResolveCache.getInstance(myElement.getProject())
        .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<MyReference>() {
          @NotNull
          @Override
          public ResolveResult[] resolve(@NotNull MyReference myReference, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, incompleteCode);
    }

    @NotNull
    private ResolveResult[] resolveInner() {
      final String methodName = myElement.getValue();
      if (methodName == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);

      if (module == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final Project project = myElement.getProject();
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

      final PsiMethod[] methods = cache.getMethodsByName(methodName, module.getModuleWithDependenciesScope());
      if (methods.length == 0) {
        return ResolveResult.EMPTY_ARRAY;
      }

      final List<ResolveResult> result = new ArrayList<ResolveResult>();
      for (PsiMethod method : methods) {
        if (checkSignature(method)) {
          result.add(new PsiElementResolveResult(method));
        }
      }
      return result.toArray(new ResolveResult[result.size()]);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);

      if (module == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final PsiClass activityClass = findActivityClass(module);
      if (activityClass == null) {
        return EMPTY_ARRAY;
      }

      final List<Object> result = new ArrayList<Object>();
      final Set<String> methodNames = new HashSet<String>();

      ClassInheritorsSearch.search(activityClass, module.getModuleWithDependenciesScope(), true).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass c) {
          for (PsiMethod method : c.getMethods()) {
            if (checkSignature(method) && methodNames.add(method.getName())) {
              result.add(createLookupElement(method));
            }
          }
          return true;
        }
      });
      return ArrayUtil.toObjectArray(result);
    }

    private static PsiClass findActivityClass(@NotNull Module module) {
      return JavaPsiFacade.getInstance(module.getProject())
        .findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));
    }

    @Nullable
    @Override
    public LocalQuickFix[] getQuickFixes() {
      final String methodName = getValue();

      if (methodName.isEmpty() || !StringUtil.isJavaIdentifier(methodName)) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      final PsiClass activity = findRelatedActivity(methodName);

      if (activity == null) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      return new LocalQuickFix[]{new MyQuickFix(methodName, activity)};
    }

    @Nullable
    private PsiClass findRelatedActivity(@NotNull String methodName) {
      final PsiFile file = myElement.getContainingFile();

      if (!(file instanceof XmlFile)) {
        return null;
      }
      final AndroidFacet facet = AndroidFacet.getInstance(file);

      if (facet == null) {
        return null;
      }
      final Computable<List<GotoRelatedItem>> computable = AndroidGotoRelatedProvider.
        getLazyItemsForXmlFile((XmlFile)file, facet);

      if (computable == null) {
        return null;
      }
      final List<GotoRelatedItem> items = computable.compute();

      if (items.isEmpty()) {
        return null;
      }
      final PsiClass activityClass = findActivityClass(facet.getModule());

      if (activityClass == null) {
        return null;
      }
      for (GotoRelatedItem item : items) {
        final PsiElement element = item.getElement();

        if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;

          if (aClass.isInheritor(activityClass, true) &&
              !findHandlerMethod(aClass, methodName)) {
            return aClass;
          }
        }
      }
      return null;
    }
  }

  public static boolean checkSignature(@NotNull PsiMethod method) {
    if (method.getReturnType() != PsiType.VOID) {
      return false;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        !method.hasModifierProperty(PsiModifier.PUBLIC)) {
      return false;
    }

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null || aClass.isInterface()) {
      return false;
    }

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) {
      return false;
    }

    final PsiType paramType = parameters[0].getType();
    if (!(paramType instanceof PsiClassType)) {
      return false;
    }

    final PsiClass paramClass = ((PsiClassType)paramType).resolve();
    return paramClass != null && AndroidUtils.VIEW_CLASS_NAME.equals(paramClass.getQualifiedName());
  }

  public static boolean findHandlerMethod(@NotNull PsiClass psiClass, @NotNull String methodName) {
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);

    for (PsiMethod method : methods) {
      if (checkSignature(method)) {
        return true;
      }
    }
    return false;
  }

  private static LookupElement createLookupElement(PsiMethod method) {
    final LookupElementBuilder builder = LookupElementBuilder.create(method, method.getName())
      .withIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
      .withPresentableText(method.getName());
    final PsiClass containingClass = method.getContainingClass();
    return containingClass != null
           ? builder.withTailText(" (" + containingClass.getQualifiedName() + ')')
           : builder;
  }

  public static class MyQuickFix extends AbstractIntentionAction implements LocalQuickFix {
    private final String myMethodName;
    private final PsiClass myClass;

    private MyQuickFix(@NotNull String methodName, @NotNull PsiClass aClass) {
      myMethodName = methodName;
      myClass = aClass;
    }

    @NotNull
    @Override
    public String getName() {
      return "Create '" + myMethodName + "(View)' in '" + myClass.getName() + "'";
    }

    @NotNull
    @Override
    public String getText() {
      return getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      AndroidCreateOnClickHandlerAction.addHandlerMethodAndNavigate(project, myClass, myMethodName);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      // it is called from inspection view or "fix all problems" action (for example) instead of invoke()
      doApplyFix(project);
    }

    public void doApplyFix(@NotNull Project project) {
      AndroidCreateOnClickHandlerAction.addHandlerMethod(project, myClass, myMethodName);
    }
  }
}
