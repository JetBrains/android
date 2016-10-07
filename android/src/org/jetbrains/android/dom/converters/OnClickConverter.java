package org.jetbrains.android.dom.converters;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;
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
public abstract class OnClickConverter extends Converter<String> implements CustomReferenceConverter<String> {
  private static final String DEFAULT_MENU_ITEM_CLASS = "android.view.MenuItem";
  private static final String ABS_MENU_ITEM_CLASS = "com.actionbarsherlock.view.MenuItem";

  public static final OnClickConverter CONVERTER_FOR_LAYOUT = new OnClickConverter() {
    @NotNull
    @Override
    public String getDefaultMethodParameterType(@NotNull PsiClass parentClass) {
      return AndroidUtils.VIEW_CLASS_NAME;
    }

    @Override
    protected boolean isAllowedMethodParameterType(@NotNull String type) {
      return AndroidUtils.VIEW_CLASS_NAME.equals(type);
    }

    @NotNull
    @Override
    public String getShortParameterName() {
      return "View";
    }
  };

  public static final OnClickConverter CONVERTER_FOR_MENU = new OnClickConverter() {
    @NotNull
    @Override
    public String getDefaultMethodParameterType(@NotNull PsiClass parentClass) {
      final Project project = parentClass.getProject();
      final PsiClass watsonClass = JavaPsiFacade.getInstance(project).findClass(
        "android.support.v4.app.Watson", GlobalSearchScope.projectScope(project));
      return watsonClass != null && parentClass.isInheritor(watsonClass, true)
             ? ABS_MENU_ITEM_CLASS
             : DEFAULT_MENU_ITEM_CLASS;
    }

    @Override
    protected boolean isAllowedMethodParameterType(@NotNull String type) {
      return DEFAULT_MENU_ITEM_CLASS.equals(type) || ABS_MENU_ITEM_CLASS.equals(type);
    }

    @NotNull
    @Override
    public String getShortParameterName() {
      return "MenuItem";
    }
  };

  @NotNull
  public abstract String getDefaultMethodParameterType(@NotNull PsiClass parentClass);

  protected abstract boolean isAllowedMethodParameterType(@NotNull String type);

  @NotNull
  public abstract String getShortParameterName();

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

  public class MyReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> {

    private MyReference(XmlAttributeValue value, TextRange range) {
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
      final PsiClass activityBaseClass = JavaPsiFacade.getInstance(project).findClass(
        AndroidUtils.ACTIVITY_BASE_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));

      if (activityBaseClass == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final List<ResolveResult> result = new ArrayList<>();
      final List<ResolveResult> resultsWithMistake = new ArrayList<>();

      for (PsiMethod method : methods) {
        final PsiClass parentClass = method.getContainingClass();

        if (parentClass != null && parentClass.isInheritor(activityBaseClass, true)) {
          if (checkSignature(method)) {
            result.add(new MyResolveResult(method, true));
          }
          else {
            resultsWithMistake.add(new MyResolveResult(method, false));
          }
        }
      }
      return result.size() > 0
             ? result.toArray(new ResolveResult[result.size()])
             : resultsWithMistake.toArray(new ResolveResult[resultsWithMistake.size()]);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);

      if (module == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final PsiClass activityClass = AndroidMissingOnClickHandlerInspection.findActivityClass(module);
      if (activityClass == null) {
        return EMPTY_ARRAY;
      }

      final List<Object> result = new ArrayList<>();
      final Set<String> methodNames = new HashSet<>();

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

    @NotNull
    public OnClickConverter getConverter() {
      return OnClickConverter.this;
    }
  }

  public boolean checkSignature(@NotNull PsiMethod method) {
    if (!PsiType.VOID.equals(method.getReturnType())) {
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

    if (paramClass == null) {
      return false;
    }
    final String paramClassName = paramClass.getQualifiedName();
    return paramClassName != null && isAllowedMethodParameterType(paramClassName);
  }

  public boolean findHandlerMethod(@NotNull PsiClass psiClass, @NotNull String methodName) {
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

  public static class MyResolveResult extends PsiElementResolveResult {
    private final boolean myHasCorrectSignature;

    public MyResolveResult(@NotNull PsiElement element, boolean hasCorrectSignature) {
      super(element);
      myHasCorrectSignature = hasCorrectSignature;
    }

    public boolean hasCorrectSignature() {
      return myHasCorrectSignature;
    }
  }
}
