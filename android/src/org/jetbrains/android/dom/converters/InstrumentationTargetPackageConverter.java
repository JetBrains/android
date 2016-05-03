package org.jetbrains.android.dom.converters;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class InstrumentationTargetPackageConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @Nullable
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Nullable
  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final Module module = context.getModule();
    return element != null && module != null
           ? new PsiReference[]{new MyReference(element, module)}
           : PsiReference.EMPTY_ARRAY;
  }

  private static class MyReference extends PsiReferenceBase<PsiElement> {
    private final Module myModule;

    public MyReference(@NotNull PsiElement element, @NotNull Module module) {
      super(element, true);
      myModule = module;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject())
        .resolveWithCaching(this, new ResolveCache.Resolver() {
          @Override
          public PsiElement resolve(@NotNull PsiReference reference, boolean incompleteCode) {
            return resolveInner();
          }
        }, false, false);
    }

    @Nullable
    private PsiElement resolveInner() {
      final String value = getValue();

      if (value.length() == 0) {
        return null;
      }
      final Ref<PsiElement> result = Ref.create();

      processApkPackageAttrs(new Processor<GenericAttributeValue<String>>() {
        @Override
        public boolean process(GenericAttributeValue<String> domValue) {
          if (value.equals(domValue.getValue())) {
            final XmlAttributeValue xmlValue = domValue.getXmlAttributeValue();

            if (xmlValue != null) {
              result.set(xmlValue);
              return false;
            }
          }
          return true;
        }
      });
      return result.get();
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final Set<String> result = new HashSet<>();

      processApkPackageAttrs(new Processor<GenericAttributeValue<String>>() {
        @Override
        public boolean process(GenericAttributeValue<String> domValue) {
          final String value = domValue.getValue();

          if (value != null && value.length() > 0) {
            result.add(value);
          }
          return true;
        }
      });
      return ArrayUtil.toStringArray(result);
    }

    private void processApkPackageAttrs(@NotNull Processor<GenericAttributeValue<String>> processor) {
      for (Module depModule : ModuleRootManager.getInstance(myModule).getDependencies()) {
        final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

        if (depFacet != null && !depFacet.isLibraryProject()) {
          final Manifest manifest = depFacet.getManifest();

          if (manifest != null) {
            final GenericAttributeValue<String> packageAttr = manifest.getPackage();

            if (packageAttr != null && !processor.process(packageAttr)) {
              return;
            }
          }
        }
      }
    }
  }
}
