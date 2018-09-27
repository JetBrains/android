package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for light implementations of inner classes of the R class, e.g. {@code R.string}.
 *
 * <p>Implementations need to implement {@link #doGetFields()}, most likely by calling one of the {@code buildResourceFields} methods.
 */
public abstract class ResourceTypeClassBase extends AndroidLightInnerClassBase {
  private static final Logger LOG = Logger.getInstance(ResourceTypeClassBase.class);

  @NotNull
  protected final ResourceType myResourceType;

  @Nullable
  private CachedValue<PsiField[]> myFieldsCache;

  protected static PsiType INT_ARRAY = PsiType.INT.createArrayType();

  public ResourceTypeClassBase(@NotNull PsiClass context, @NotNull ResourceType resourceType) {
    super(context, resourceType.getName());
    myResourceType = resourceType;
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@Nullable ResourceManager manager,
                                                  @NotNull ResourceRepository repository,
                                                  @NotNull ResourceNamespace namespace,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context) {
    Map<String, PsiType> fieldNames = new HashMap<>();
    PsiType basicType = ResourceType.STYLEABLE == resourceType ? INT_ARRAY : PsiType.INT;

    for (String resName : repository.getResources(namespace, resourceType).keySet()) {
      fieldNames.put(resName, basicType);
    }

    if (ResourceType.STYLEABLE == resourceType) {
      Collection<ResourceItem> items = repository.getResources(namespace, ResourceType.STYLEABLE).values();
      for (ResourceItem item : items) {
        StyleableResourceValue value = (StyleableResourceValue)item.getResourceValue();
        if (value != null) {
          List<AttrResourceValue> attributes = value.getAllAttributes();
          for (AttrResourceValue attr : attributes) {
            if (manager == null || manager.isResourcePublic(attr.getResourceType().getName(), attr.getName())) {
              ResourceNamespace attrNamespace = attr.getNamespace();
              String packageName = attrNamespace.getPackageName();
              if (attrNamespace.equals(namespace) || StringUtil.isEmpty(packageName)) {
                fieldNames.put(item.getName() + '_' + attr.getName(), PsiType.INT);
              }
              else {
                fieldNames.put(item.getName() + '_' + packageName.replace('.', '_') + '_' + attr.getName(), PsiType.INT);
              }
            }
          }
        }
      }
    }

    return buildResourceFields(fieldNames, resourceType, context, fieldModifier);
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull Map<String, PsiType> fieldNames,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier) {
    PsiField[] result = new PsiField[fieldNames.size()];
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    int idIterator = resourceType.ordinal() * 100000;
    int i = 0;

    for (Map.Entry<String, PsiType> entry : fieldNames.entrySet()) {
      String fieldName = AndroidResourceUtil.getFieldNameByResourceName(entry.getKey());
      PsiType type = entry.getValue();
      int id = -(idIterator++);
      AndroidLightField field = new AndroidLightField(fieldName,
                                                      context,
                                                      type,
                                                      fieldModifier,
                                                      fieldModifier == AndroidLightField.FieldModifier.FINAL ? id : null);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(id), field));
      result[i++] = field;
    }
    return result;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    if (myFieldsCache == null) {
      myFieldsCache = CachedValuesManager.getManager(getProject()).createCachedValue(
        () -> {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Recomputing fields for " + this);
          }
          return CachedValueProvider.Result.create(doGetFields(), getFieldsDependencies());
        });
    }
    return myFieldsCache.getValue();
  }

  @NotNull
  protected abstract PsiField[] doGetFields();

  /**
   * Dependencies (as defined by {@link CachedValueProvider.Result#getDependencyItems()}) for the cached set of inner classes computed by
   * {@link #doGetFields()}.
   */
  @NotNull
  protected abstract Object[] getFieldsDependencies();

  @NotNull
  public ResourceType getResourceType() {
    return myResourceType;
  }
}
