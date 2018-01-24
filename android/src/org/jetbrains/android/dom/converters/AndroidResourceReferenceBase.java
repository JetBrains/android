package org.jetbrains.android.dom.converters;

import com.android.SdkConstants;
import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.TOOLS_URI;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceReferenceBase extends PsiReferenceBase.Poly<XmlElement> {
  protected final AndroidFacet myFacet;
  protected final ResourceValue myResourceValue;

  public AndroidResourceReferenceBase(@NotNull GenericDomValue value,
                                      @Nullable TextRange range,
                                      @NotNull ResourceValue resourceValue,
                                      @NotNull AndroidFacet facet) {
    super(DomUtil.getValueElement(value), range, true);
    myResourceValue = resourceValue;
    myFacet = facet;
  }

  @Nullable
  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @NotNull
  public ResourceValue getResourceValue() {
    return myResourceValue;
  }

  @NotNull
  public PsiElement[] computeTargetElements() {
    final ResolveResult[] resolveResults = multiResolve(false);
    final List<PsiElement> results = new ArrayList<>();

    for (ResolveResult result : resolveResults) {
      PsiElement element = result.getElement();

      if (element instanceof LazyValueResourceElementWrapper) {
        element = ((LazyValueResourceElementWrapper)element).computeElement();
      }

      if (element instanceof ResourceElementWrapper) {
        element = ((ResourceElementWrapper)element).getWrappee();
      }

      if (element != null) {
        results.add(element);
      }
    }
    return results.toArray(new PsiElement[results.size()]);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(myElement.getProject())
      .resolveWithCaching(this, (reference, incompleteCode1) -> resolveInner(), false, incompleteCode);
  }

  @NotNull
  private ResolveResult[] resolveInner() {
    final List<PsiElement> elements = new ArrayList<>();
    final boolean attrReference = myResourceValue.getPrefix() == '?';
    collectTargets(myFacet, myResourceValue, elements, attrReference);
    final List<ResolveResult> result = new ArrayList<>();

    if (elements.isEmpty() && myResourceValue.getResourceName() != null &&
        !AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(myResourceValue.getNamespace())) {
      // Dynamic items do not appear in the XML scanning file index; look for
      // these in the resource repositories.
      LocalResourceRepository resources = AppResourceRepository.getOrCreateInstance(myFacet.getModule());
      ResourceType resourceType = myResourceValue.getType();
      if (resourceType != null && (resourceType != ResourceType.ATTR || attrReference)) { // If not, it could be some broken source, such as @android/test
        assert resources != null;

        String resourceName = myResourceValue.getResourceName();
        if (resourceType == ResourceType.SAMPLE_DATA) {
          resourceName = SampleDataManager.getResourceNameFromSampleReference(resourceName);
        }

        ResourceNamespace namespace = ResourceNamespace.TODO;

        // In non-namespaced project we allowed the usage of "tools:" without proper xmlns declaration.
        // TODO(namespaces): revisit this once we have code-completion working in namespaced projects.
        if (resourceType == ResourceType.SAMPLE_DATA &&
            SdkConstants.TOOLS_NS_NAME.equals(myResourceValue.getNamespace()) &&
            ResourceRepositoryManager.getOrCreateInstance(myFacet).getNamespacing() == AaptOptions.Namespacing.DISABLED) {
          namespace = ResourceNamespace.TOOLS;
        }

        List<ResourceItem> items = resources.getResourceItems(namespace, resourceType, resourceName);
        if (FolderTypeRelationship.getRelatedFolders(resourceType).contains(ResourceFolderType.VALUES)) {
          for (ResourceItem item : items) {
            XmlTag tag = LocalResourceRepository.getItemTag(myFacet.getModule().getProject(), item);
            if (tag != null) {
              elements.add(tag);
            } else if (item instanceof DynamicResourceValueItem) {
              result.add(((DynamicResourceValueItem)item).createResolveResult());
            }
          }
        }
        else if (resourceType == ResourceType.SAMPLE_DATA && myElement.getParent() instanceof XmlAttribute) {
          // The mock references can only be applied to tools: attributes
          XmlAttribute attribute = (XmlAttribute)myElement.getParent();
          if (TOOLS_URI.equals(attribute.getNamespace())) {
            items.stream()
              .filter(SampleDataResourceItem.class::isInstance)
              .map(SampleDataResourceItem.class::cast)
              .forEach(sampleDataItem -> result.add(new ResolveResult() {
                @Nullable
                @Override
                public PsiElement getElement() {
                  return sampleDataItem.getPsiElement();
                }

                @Override
                public boolean isValidResult() {
                  return true;
                }
              }));
          }
        }
      }
    }

    if (elements.size() > 1) {
      elements.sort(AndroidResourceUtil.RESOURCE_ELEMENT_COMPARATOR);
    }

    for (PsiElement target : elements) {
      result.add(new PsiElementResolveResult(target));
    }

    return result.toArray(new ResolveResult[result.size()]);
  }

  private void collectTargets(AndroidFacet facet, ResourceValue resValue, List<PsiElement> elements, boolean attrReference) {
    ResourceType resType = resValue.getType();
    if (resType == null) {
      return;
    }
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(resValue.getNamespace(), myElement);
    if (manager != null) {
      String resName = resValue.getResourceName();
      if (resName != null) {
        manager.collectLazyResourceElements(resType.getName(), resName, attrReference, myElement, elements);
      }
    }
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof LazyValueResourceElementWrapper) {
      element = ((LazyValueResourceElementWrapper)element).computeElement();

      if (element == null) {
        return false;
      }
    }

    final ResolveResult[] results = multiResolve(false);
    final PsiFile psiFile = element.getContainingFile();
    final VirtualFile vFile = psiFile != null ? psiFile.getVirtualFile() : null;

    for (ResolveResult result : results) {
      final PsiElement target = result.getElement();

      if (element.getManager().areElementsEquivalent(target, element)) {
        return true;
      }

      if (target instanceof LazyValueResourceElementWrapper && vFile != null) {
        final ValueResourceInfo info = ((LazyValueResourceElementWrapper)target).getResourceInfo();

        if (info.getContainingFile().equals(vFile)) {
          final XmlAttributeValue realTarget = info.computeXmlElement();

          if (element.getManager().areElementsEquivalent(realTarget, element)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
