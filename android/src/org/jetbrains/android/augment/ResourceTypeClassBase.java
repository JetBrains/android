package org.jetbrains.android.augment;

import com.android.resources.ResourceType;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ResourceTypeClassBase extends AndroidLightClass {
  private CachedValue<PsiField[]> myFieldsCache;

  public ResourceTypeClassBase(PsiClass context, String name) {
    super(context, name);
  }

  @NotNull
  static PsiField[] buildResourceFields(@NotNull ResourceManager manager,
                                        boolean nonFinal,
                                        @NotNull String resClassName,
                                        @NotNull final PsiClass context) {
    ResourceType resourceType = ResourceType.getEnum(resClassName);
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }
    final Map<String, PsiType> fieldNames = new HashMap<String, PsiType>();
    final boolean styleable = ResourceType.STYLEABLE.equals(resourceType);
    final PsiType basicType = styleable ? PsiType.INT.createArrayType() : PsiType.INT;

    for (String resName : manager.getResourceNames(resourceType)) {
      fieldNames.put(resName, basicType);
    }

    if (styleable) {
      for (ResourceEntry entry : manager.getValueResourceEntries(ResourceType.ATTR)) {
        final String resName = entry.getName();
        final String resContext = entry.getContext();

        if (resContext.length() > 0) {
          fieldNames.put(resContext + '_' + resName, PsiType.INT);
        }
      }
    }
    final PsiField[] result = new PsiField[fieldNames.size()];
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    int idIterator = resourceType.ordinal() * 100000;
    int i = 0;

    for (Map.Entry<String, PsiType> entry : fieldNames.entrySet()) {
      final String fieldName = AndroidResourceUtil.getFieldNameByResourceName(entry.getKey());
      final PsiType type = entry.getValue();
      final int id = -(idIterator++);
      final AndroidLightField field =
        new AndroidLightField(fieldName, context, type, !nonFinal, nonFinal ? null : id);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(id), field));
      result[i++] = field;
    }
    return result;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(getProject()).createCachedValue(new CachedValueProvider<PsiField[]>() {
        @Override
        public Result<PsiField[]> compute() {
          return Result.create(doGetFields(), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }
      });
    }
    return myFieldsCache.getValue();
  }

  @NotNull
  protected abstract PsiField[] doGetFields();
}
