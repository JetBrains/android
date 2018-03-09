package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
                                        @NotNull AbstractResourceRepository repository,
                                        @NotNull ResourceNamespace namespace,
                                        boolean nonFinal,
                                        @NotNull String resClassName,
                                        @NotNull PsiClass context) {
    ResourceType resourceType = ResourceType.getEnum(resClassName);
    if (resourceType == null) {
      return PsiField.EMPTY_ARRAY;
    }
    else if (resourceType == ResourceType.STYLEABLE) {
      // TODO(b/74325205): remove the need for this.
      resourceType = ResourceType.DECLARE_STYLEABLE;
    }
    Map<String, PsiType> fieldNames = new HashMap<>();
    PsiType basicType = ResourceType.DECLARE_STYLEABLE == resourceType ? PsiType.INT.createArrayType() : PsiType.INT;

    for (String resName : repository.getItemsOfType(namespace, resourceType)) {
      fieldNames.put(resName, basicType);
    }

    if (ResourceType.DECLARE_STYLEABLE == resourceType) {
      List<ResourceItem> items = repository.getResourceItems(namespace, ResourceType.DECLARE_STYLEABLE);
      for (ResourceItem item : items) {
        DeclareStyleableResourceValue value = (DeclareStyleableResourceValue)item.getResourceValue();
        if (value != null) {
          List<AttrResourceValue> attributes = value.getAllAttributes();
          for (AttrResourceValue attr : attributes) {
            if (manager.isResourcePublic(attr.getResourceType().getName(), attr.getName())) {
              ResourceNamespace attrNamespace = attr.getNamespace();
              String packageName = attrNamespace.getPackageName();
              if (attrNamespace.equals(namespace) || StringUtil.isEmpty(packageName)) {
                fieldNames.put(item.getName() + '_' + attr.getName(), PsiType.INT);
              } else {
                fieldNames.put(item.getName() + '_' + packageName.replace('.', '_') + '_' + attr.getName(), PsiType.INT);
              }
            }
          }
        }
      }
    }

    PsiField[] result = new PsiField[fieldNames.size()];
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    int idIterator = resourceType.ordinal() * 100000;
    int i = 0;

    for (Map.Entry<String, PsiType> entry : fieldNames.entrySet()) {
      String fieldName = AndroidResourceUtil.getFieldNameByResourceName(entry.getKey());
      PsiType type = entry.getValue();
      int id = -(idIterator++);
      AndroidLightField field = new AndroidLightField(fieldName, context, type, !nonFinal, nonFinal ? null : id);
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
