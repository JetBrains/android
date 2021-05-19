package org.jetbrains.android.augment;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.base.Verify;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import com.intellij.util.ArrayUtil;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for light implementations of inner classes of the R class, e.g. {@code R.string}.
 *
 * <p>Implementations need to implement {@link #doGetFields()}, most likely by calling one of the {@code buildResourceFields} methods.
 */
public abstract class InnerRClassBase extends AndroidLightInnerClassBase {
  private static final Logger LOG = Logger.getInstance(InnerRClassBase.class);

  @NotNull
  protected final ResourceType myResourceType;

  @Nullable
  private CachedValue<PsiField[]> myFieldsCache;

  protected static PsiType INT_ARRAY = PsiType.INT.createArrayType();

  public InnerRClassBase(@NotNull PsiClass context, @NotNull ResourceType resourceType) {
    super(context, resourceType.getName());
    myResourceType = resourceType;
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull ResourceRepository repository,
                                                  @NotNull ResourceNamespace namespace,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier,
                                                  @NotNull BiPredicate<ResourceType, String> isPublic,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context) {
    Collection<Pair<String, ResourceVisibility>> otherFields = new ArrayList<>();
    Collection<Pair<String, ResourceVisibility>> styleableFields = new ArrayList<>();
    Collection<StyleableAttrFieldUrl> styleableAttrFields = new ArrayList<>();

    if (resourceType == ResourceType.STYLEABLE) {
      ListMultimap<String, ResourceItem> map = repository.getResources(namespace, resourceType);
      styleableFields.addAll(
        ContainerUtil.map(map.keySet(),
                          it -> new Pair<>(it, isPublic.test(resourceType, it) ? ResourceVisibility.PUBLIC : ResourceVisibility.PRIVATE)));

      for (ResourceItem item : map.values()) {
        StyleableResourceValue value = (StyleableResourceValue)item.getResourceValue();
        if (value != null) {
          List<AttrResourceValue> attributes = value.getAllAttributes();
          for (AttrResourceValue attr : attributes) {
            if (isPublic.test(attr.getResourceType(), attr.getName())) {
              ResourceNamespace attrNamespace = attr.getNamespace();
              styleableAttrFields.add(new StyleableAttrFieldUrl(
                new ResourceReference(namespace, ResourceType.STYLEABLE, item.getName()),
                new ResourceReference(attrNamespace, ResourceType.ATTR, attr.getName())
              ));
            }
          }
        }
      }
    }
    else {
      otherFields.addAll(
        ContainerUtil.map(repository.getResourceNames(namespace, resourceType),
                          it -> new Pair<>(it, isPublic.test(resourceType, it) ? ResourceVisibility.PUBLIC : ResourceVisibility.PRIVATE)));
    }

    return buildResourceFields(otherFields, styleableFields, styleableAttrFields, resourceType, context, fieldModifier);
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull Collection<Pair<String, ResourceVisibility>> otherFields,
                                                  @NotNull Collection<Pair<String, ResourceVisibility>> styleableFields,
                                                  @NotNull Collection<StyleableAttrFieldUrl> styleableAttrFields,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier) {
    PsiField[] result = new PsiField[otherFields.size() + styleableFields.size() + styleableAttrFields.size()];
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());

    int nextId = resourceType.ordinal() * 100000;
    int i = 0;

    for (Pair<String, ResourceVisibility> fieldPair : otherFields) {
      int fieldId = nextId++;
      ResourceLightField field = new ResourceLightField(fieldPair.getFirst(),
                                                        context,
                                                        PsiType.INT,
                                                        fieldModifier,
                                                        fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null,
                                                        fieldPair.getSecond());
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (Pair<String, ResourceVisibility> fieldName : styleableFields) {
      int fieldId = nextId++;
      ResourceLightField field = new ResourceLightField(fieldName.getFirst(),
                                                        context,
                                                        INT_ARRAY,
                                                        fieldModifier,
                                                        fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null,
                                                        fieldName.getSecond());
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (StyleableAttrFieldUrl fieldContents : styleableAttrFields) {
      int fieldId = nextId++;
      AndroidLightField field = new StyleableAttrLightField(fieldContents,
                                                            context,
                                                            fieldModifier,
                                                            fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null);
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
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

          ModificationTracker dependencies = getFieldsDependencies();

          // When ResourceRepositoryManager's caches are dropped, new instances of repositories are created and the old ones
          // stop incrementing their modification count. We need to make sure the CachedValue doesn't hold on to any particular repository
          // instance and instead reads the modification count of the "current" instance.
          Verify.verify(!(dependencies instanceof ResourceRepository), "Resource repository leaked in a CachedValue.");

          return CachedValueProvider.Result.create(doGetFields(), dependencies);
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
  protected abstract ModificationTracker getFieldsDependencies();

  @NotNull
  public ResourceType getResourceType() {
    return myResourceType;
  }
}
