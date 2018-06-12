package org.jetbrains.android.augment;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.android.augment.AndroidLightField.FieldModifier;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class ManifestInnerClass extends AndroidLightInnerClassBase {
  private CachedValue<PsiField[]> myFieldsCache;
  private final AndroidFacet myFacet;

  ManifestInnerClass(@NotNull AndroidFacet facet, @NotNull String name, @NotNull PsiClass context) {
    super(context, name);
    myFacet = facet;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(getProject()).createCachedValue(() -> {
        final Manifest manifest = myFacet.getManifest();
        if (manifest == null) {
          return CachedValueProvider.Result.create(PsiField.EMPTY_ARRAY, PsiModificationTracker.MODIFICATION_COUNT);
        }
        final List<Pair<String, String>> pairs = doGetFields(manifest);

        final PsiField[] result = new PsiField[pairs.size()];
        final PsiClassType stringType = PsiType.getJavaLangString(myManager, GlobalSearchScope.allScope(getProject()));
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        int i = 0;
        for (Pair<String, String> pair : pairs) {
          final AndroidLightField field =
            new AndroidLightField(pair.getFirst(), this, stringType, FieldModifier.FINAL, pair.getSecond());
          field.setInitializer(factory.createExpressionFromText("\"" + pair.getSecond() + "\"", field));
          result[i++] = field;
        }

        final XmlElement xmlElement = manifest.getXmlElement();
        final PsiFile psiManifestFile = xmlElement != null ? xmlElement.getContainingFile() : null;
        return CachedValueProvider.Result.create(result, psiManifestFile != null ? psiManifestFile : PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    return myFieldsCache.getValue();
  }

  @NotNull
  protected abstract List<Pair<String, String>> doGetFields(@NotNull Manifest manifest);
}
