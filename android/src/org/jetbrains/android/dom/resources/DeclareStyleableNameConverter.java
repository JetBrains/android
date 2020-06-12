package org.jetbrains.android.dom.resources;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.tools.idea.res.psi.ResourceRepositoryToPsiResolver;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import java.util.HashSet;
import com.intellij.util.xml.*;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class DeclareStyleableNameConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final Module module = context.getModule();
    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        return new PsiReference[]{new MyReference(facet, value)};
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static class MyReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private final GenericDomValue<String> myValue;
    private final AndroidFacet myFacet;

    MyReference(@NotNull AndroidFacet facet, @NotNull GenericDomValue<String> value) {
      super(DomUtil.getValueElement(value), true);
      myFacet = facet;
      myValue = value;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      if (StudioFlags.RESOLVE_USING_REPOS.get() && element instanceof ResourceReferencePsiElement) {
        if (((ResourceReferencePsiElement)element).getResourceReference().getResourceType().equals(ResourceType.STYLEABLE)) {
          return ((ResourceReferencePsiElement)element).getResourceReference().getName().equals(myValue.getValue());
        }
      }
      return super.isReferenceTo(element);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return ResolveCache.getInstance(myElement.getProject())
        .resolveWithCaching(this, new ResolveCache.PolyVariantResolver<MyReference>() {
          @NotNull
          @Override
          public ResolveResult[] resolve(@NotNull MyReference reference, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, incompleteCode);
    }

    private ResolveResult[] resolveInner() {
      String value = myValue.getValue();
      if (value == null) {
        return ResolveResult.EMPTY_ARRAY;
      }
      if (StudioFlags.RESOLVE_USING_REPOS.get()) {
        // In the new pipeline the declare styleable name will point to the ResourceReferencePsiElement, similar to the corresponding field.
        ResourceNamespace resourceNamespace = ResourceRepositoryManager.getInstance(myFacet).getNamespace();
        return ResourceRepositoryToPsiResolver.INSTANCE.resolveReference(
          new ResourceReference(resourceNamespace, ResourceType.STYLEABLE, value), myElement, myFacet);
      }
      // Search for custom views with the same name as the declare styleable, such that
      // you can navigate from the XML styleable declaration to the corresponding custom view
      final PsiClass[] classes = PsiShortNamesCache.getInstance(myElement.getProject())
        .getClassesByName(value, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (classes.length == 0) {
        return ResolveResult.EMPTY_ARRAY;
      }
      final ResolveResult[] result = new ResolveResult[classes.length];
      for (int i = 0; i < result.length; i++) {
        result[i] = new PsiElementResolveResult(classes[i]);
      }
      return result;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final PsiClass viewClass = JavaPsiFacade.getInstance(myElement.getProject())
        .findClass(AndroidUtils.VIEW_CLASS_NAME, myFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (viewClass == null) {
        return EMPTY_ARRAY;
      }
      final Set<Object> shortNames = new HashSet<>();

      ClassInheritorsSearch.search(viewClass, myFacet.getModule().getModuleWithDependenciesScope(), true).
        forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass aClass) {
            final String name = aClass.getName();

            if (name != null) {
              shortNames.add(JavaLookupElementBuilder.forClass(aClass, name, true));
            }
            return true;
          }
        });
      return shortNames.toArray();
    }
  }
}
