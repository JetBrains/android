package org.jetbrains.android.dom.converters;

import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidResourceReferenceBase extends PsiReferenceBase.Poly<XmlElement> {
  protected final AndroidFacet myFacet;
  protected final ResourceValue myResourceValue;

  public AndroidResourceReferenceBase(@NotNull GenericDomValue<?> value,
                                      @Nullable TextRange range,
                                      @NotNull ResourceValue resourceValue,
                                      @NotNull AndroidFacet facet) {
    super(DomUtil.getValueElement(value), range, true);
    myResourceValue = resourceValue;
    myFacet = facet;
  }

  public boolean includeDynamicFeatures() {
    return false;
  }

  @Override
  public @Nullable PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public @NotNull ResourceValue getResourceValue() {
    return myResourceValue;
  }

  public PsiElement[] computeTargetElements() {
    final ResolveResult[] resolveResults = multiResolve(false);
    final List<PsiElement> results = new ArrayList<>();

    for (ResolveResult result : resolveResults) {
      PsiElement element = result.getElement();

      if (element instanceof ResourceElementWrapper) {
        results.add(((ResourceElementWrapper)element).getWrappedElement());
      }
      else if (element instanceof ResourceReferencePsiElement) {
        PsiElement[] targets = AndroidResourceToPsiResolver.getInstance()
          .getGotoDeclarationTargets(((ResourceReferencePsiElement)element).getResourceReference(), myElement);
        results.addAll(Arrays.asList(targets));
      }
      else {
        results.add(element);
      }
    }
    return results.toArray(PsiElement.EMPTY_ARRAY);
  }


  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(myElement.getProject())
      .resolveWithCaching(this, (reference, incompleteCode1) -> resolveInner(), false, incompleteCode);
  }

  private ResolveResult[] resolveInner() {
    if (includeDynamicFeatures()) {
      return AndroidResourceToPsiResolver.getInstance().resolveReferenceWithDynamicFeatureModules(myResourceValue, myElement, myFacet);
    } else {
      return AndroidResourceToPsiResolver.getInstance().resolveReference(myResourceValue, myElement, myFacet);
    }
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    ResolveResult[] results = multiResolve(false);

    for (ResolveResult result : results) {
      PsiElement target = result.getElement();
      if (element.getManager().areElementsEquivalent(target, element)) {
        return true;
      }
    }
    return false;
  }
}
