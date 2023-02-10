package org.jetbrains.android.augment;

import static org.jetbrains.android.AndroidResolveScopeEnlarger.BACKING_CLASS;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.base.Verify;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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

  protected static final PsiType INT_ARRAY = PsiType.INT.createArrayType();

  protected static final Predicate<ResourceItem> ACCESSIBLE_RESOURCE_FILTER = resource -> {
    if (!resource.getNamespace().equals(ResourceNamespace.ANDROID) && resource.getLibraryName() == null) {
      return true; // Project resource.
    }
    if (resource instanceof ResourceItemWithVisibility) {
      return ((ResourceItemWithVisibility)resource).getVisibility() == ResourceVisibility.PUBLIC;
    }
    throw new AssertionError("Library resource " + resource.getType() + '/' + resource.getName() + " of type " +
                             resource.getClass().getSimpleName() + " doesn't implement ResourceItemWithVisibility");
  };

  public InnerRClassBase(@NotNull PsiClass context, @NotNull ResourceType resourceType) {
    super(context, resourceType.getName());
    myResourceType = resourceType;
  }

  protected static PsiField[] buildResourceFields(@NotNull ResourceRepository repository,
                                                  @NotNull ResourceNamespace namespace,
                                                  @Nullable StudioResourceRepositoryManager studioResourceRepositoryManager,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier,
                                                  @NotNull Predicate<ResourceItem> resourceFilter,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context) {
    Map<String, ResourceVisibility> otherFields = new LinkedHashMap<>();
    Map<String, ResourceVisibility> styleableFields = new LinkedHashMap<>();
    Collection<StyleableAttrFieldUrl> styleableAttrFields = new ArrayList<>();

    ListMultimap<String, ResourceItem> map = repository.getResources(namespace, resourceType);
    for (ResourceItem resource : map.values()) {
      ResourceVisibility visibility = resourceFilter.test(resource) ? ResourceVisibility.PUBLIC : ResourceVisibility.PRIVATE;
      if (resourceType == ResourceType.STYLEABLE) {
        styleableFields.merge(resource.getName(), visibility, ResourceVisibility::max);
        StyleableResourceValue value = (StyleableResourceValue)resource.getResourceValue();
        if (value != null) {
          List<AttrResourceValue> attributes = value.getAllAttributes();
          for (AttrResourceValue attr : attributes) {
            ResourceNamespace attrNamespace = attr.getNamespace();
            ResourceRepository attrRepository = studioResourceRepositoryManager == null ?
                                                repository : studioResourceRepositoryManager.getResourcesForNamespace(attrNamespace);
            if (attrRepository != null) {
              List<ResourceItem> attrResources = attrRepository.getResources(attrNamespace, ResourceType.ATTR, attr.getName());
              if (!attrResources.isEmpty()) {
                ResourceItem attrResource = attrResources.get(0);
                if (resourceFilter.test(attrResource)) {
                  styleableAttrFields.add(new StyleableAttrFieldUrl(
                    new ResourceReference(namespace, ResourceType.STYLEABLE, resource.getName()),
                    new ResourceReference(attrNamespace, ResourceType.ATTR, attr.getName())
                  ));
                }
              }
            }
          }
        }
      }
      else {
        otherFields.merge(resource.getName(), visibility, ResourceVisibility::max);
      }
    }

    return buildResourceFields(otherFields, styleableFields, styleableAttrFields, resourceType, context, fieldModifier);
  }

  /**
   * Returns the inner R class int id for the given fieldName.
   * @param innerRClass the {@link PsiClass} of the an inner class of the R class containing a specific resource type.
   * @param fieldName the name of the field to look up.
   * @param defaultValue a default value returned if the field can not be found.
   */
  @NotNull
  private static Integer findBackingField(@Nullable PsiClass innerRClass, String fieldName, int defaultValue) {
    if (innerRClass == null) {
      return defaultValue;
    }

    PsiField backingField = innerRClass.findFieldByName(fieldName, false);
    Object backingFieldValue = backingField != null ? backingField.computeConstantValue() : null;
    return backingFieldValue instanceof Integer ? (Integer)backingFieldValue : defaultValue;
  }

  @NotNull
  protected static PsiField[] buildResourceFields(@NotNull Map<String, ResourceVisibility> otherFields,
                                                  @NotNull Map<String, ResourceVisibility> styleableFields,
                                                  @NotNull Collection<StyleableAttrFieldUrl> styleableAttrFields,
                                                  @NotNull ResourceType resourceType,
                                                  @NotNull PsiClass context,
                                                  @NotNull AndroidLightField.FieldModifier fieldModifier) {
    PsiField[] result = new PsiField[otherFields.size() + styleableFields.size() + styleableAttrFields.size()];
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    SmartPsiElementPointer<PsiClass> rClassSmartPointer =
      context.getContainingFile().getViewProvider().getVirtualFile().getUserData(BACKING_CLASS);
    PsiClass rClass = rClassSmartPointer != null ? rClassSmartPointer.getElement() : null;
    PsiClass innerRClass = rClass != null ? rClass.findInnerClassByName(resourceType.getName(), false) : null;

    int nextId = resourceType.ordinal() * 100000;
    int i = 0;

    for (Map.Entry<String, ResourceVisibility> entry : otherFields.entrySet()) {
      int fieldId = findBackingField(innerRClass, entry.getKey(), nextId++);

      ResourceLightField field = new ResourceLightField(entry.getKey(),
                                                        context,
                                                        PsiType.INT,
                                                        fieldModifier,
                                                        fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null,
                                                        entry.getValue());
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (Map.Entry<String, ResourceVisibility> entry : styleableFields.entrySet()) {
      int fieldId = findBackingField(innerRClass, entry.getKey(), nextId++);

      ResourceLightField field = new ResourceLightField(entry.getKey(),
                                                        context,
                                                        INT_ARRAY,
                                                        fieldModifier,
                                                        fieldModifier == AndroidLightField.FieldModifier.FINAL ? fieldId : null,
                                                        entry.getValue());
      field.setInitializer(factory.createExpressionFromText(Integer.toString(fieldId), field));
      result[i++] = field;
    }

    for (StyleableAttrFieldUrl fieldContents : styleableAttrFields) {
      int fieldId = findBackingField(innerRClass, fieldContents.toFieldName(), nextId++);

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

  @Slow
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
