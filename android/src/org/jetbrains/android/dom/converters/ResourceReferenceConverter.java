/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.converters;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.AdditionalConverter;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidResourceUtil.VALUE_RESOURCE_TYPES;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

/**
 * @author yole
 */
public class ResourceReferenceConverter extends ResolvingConverter<ResourceValue>
  implements CustomReferenceConverter<ResourceValue>, AttributeValueDocumentationProvider {
  private static final ImmutableSet<String> TOP_PRIORITY_VALUES =
    ImmutableSet.of(VALUE_MATCH_PARENT, VALUE_WRAP_CONTENT);
  private final Set<ResourceType> myResourceTypes;
  private ResolvingConverter<String> myAdditionalConverter;
  private boolean myAdditionalConverterSoft = false;
  private boolean myWithPrefix = true;
  private boolean myWithExplicitResourceType = true;
  private boolean myQuiet = false;
  private boolean myAllowAttributeReferences = true;
  /**
   * Whether the completion suggestion should be expanded or not
   * (e.g. If false, displays @style/ and @color/. If true, displays @style/myStyle1, @style/myStyle2, @color/myColor1, @color/black).
   */
  private boolean myExpandedCompletionSuggestion = true;
  private boolean myAllowLiterals = true;
  private @Nullable AttributeDefinition myAttributeDefinition = null;

  public ResourceReferenceConverter() {
    this(EnumSet.noneOf(ResourceType.class));
  }

  public ResourceReferenceConverter(@NotNull Collection<ResourceType> resourceTypes) {
    myResourceTypes = EnumSet.copyOf(resourceTypes);
  }

  // TODO: it should be possible to get rid of AttributeDefinition dependency and use ResourceManager
  // to acquire AttributeDefinition when needed.
  public ResourceReferenceConverter(@NotNull Collection<ResourceType> resourceTypes, @Nullable AttributeDefinition attributeDefinition) {
    myResourceTypes = EnumSet.copyOf(resourceTypes);
    myAttributeDefinition = attributeDefinition;
  }

  public void setAllowLiterals(boolean allowLiterals) {
    myAllowLiterals = allowLiterals;
  }

  /**
   * @param resourceType the resource type to be used in the resolution (e.g. "style")
   * @param withPrefix if true, this will force all the resolved references to contain the reference prefix @
   * @param withExplicitResourceType if true, this will force the resourceType to be part of the resolved name (e.g. "@style/")
   */
  public ResourceReferenceConverter(@NotNull ResourceType resourceType, boolean withPrefix, boolean withExplicitResourceType) {
    myResourceTypes = EnumSet.of(resourceType);
    myWithPrefix = withPrefix;
    myWithExplicitResourceType = withExplicitResourceType;
  }

  public void setAdditionalConverter(@Nullable ResolvingConverter<String> additionalConverter, boolean soft) {
    myAdditionalConverter = additionalConverter;
    myAdditionalConverterSoft = soft;
  }

  public void setQuiet(boolean quiet) {
    myQuiet = quiet;
  }

  public void setExpandedCompletionSuggestion(boolean expandedCompletionSuggestion) {
    myExpandedCompletionSuggestion = expandedCompletionSuggestion;
  }

  public void setAllowAttributeReferences(boolean allowAttributeReferences) {
    myAllowAttributeReferences = allowAttributeReferences;
  }

  @NotNull
  private static String getPackagePrefix(@Nullable String resourcePackage, boolean withPrefix) {
    String prefix = withPrefix ? "@" : "";
    if (resourcePackage == null) {
      return prefix;
    }
    return prefix + resourcePackage + ':';
  }

  @Nullable
  static String getValue(XmlElement element) {
    if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValue();
    }
    else if (element instanceof XmlTag) {
      return ((XmlTag)element).getValue().getText();
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<? extends ResourceValue> getVariants(ConvertContext context) {
    Set<ResourceValue> result = new HashSet<>();
    Module module = context.getModule();
    if (module == null) return result;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) return result;

    final Set<ResourceType> recommendedTypes = getResourceTypes(context);

    if (recommendedTypes.contains(ResourceType.BOOL) && recommendedTypes.size() < VALUE_RESOURCE_TYPES.size()) {
      // Is this resource reference expected to be a @bool reference? Specifically
      // check that it's not allowed to be *all* resource types since that's a fallback
      // for when we don't have metadata, and we don't want to show true and false
      // as possible completions for things like com.google.android.gms.version
      result.add(ResourceValue.literal(VALUE_TRUE));
      result.add(ResourceValue.literal(VALUE_FALSE));
    }

    // hack to check if it is a real id attribute
    if (recommendedTypes.contains(ResourceType.ID) && recommendedTypes.size() == 1) {
      result.add(ResourceValue.reference(NEW_ID_PREFIX));
    }

    XmlElement element = context.getXmlElement();
    if (element == null) return result;
    String value = getValue(element);
    assert value != null;

    boolean startsWithRefChar = StringUtil.startsWithChar(value, '@');
    if (!myQuiet || startsWithRefChar) {
      String resourcePackage = null;
      // Retrieve the system prefix depending on the prefix settings ("@android:" or "android:")
      String systemPrefix = getPackagePrefix(SYSTEM_RESOURCE_PACKAGE, myWithPrefix || startsWithRefChar);
      if (value.startsWith(systemPrefix)) {
        resourcePackage = SYSTEM_RESOURCE_PACKAGE;
      }
      else {
        result.add(ResourceValue.literal(systemPrefix));
      }
      final char prefix = myWithPrefix || startsWithRefChar ? '@' : 0;

      if (value.startsWith(NEW_ID_PREFIX)) {
        addVariantsForIdDeclaration(result, facet, prefix, value);
      }

      if (recommendedTypes.size() >= 1 && myExpandedCompletionSuggestion) {
        // We will add the resource type (e.g. @style/) if the current value starts like a reference using @
        final boolean explicitResourceType = startsWithRefChar || myWithExplicitResourceType;
        for (final ResourceType type : recommendedTypes) {
          addResourceReferenceValues(facet, element, prefix, type, resourcePackage, result, explicitResourceType);
        }
      }
      else {
        final Set<ResourceType> filteringSet = SYSTEM_RESOURCE_PACKAGE.equals(resourcePackage)
                                               ? null
                                               : getResourceTypesInCurrentModule(facet);

        for (ResourceType resourceType : ResourceType.values()) {
          final String type = resourceType.getName();
          String typePrefix = getTypePrefix(resourcePackage, type);
          if (value.startsWith(typePrefix)) {
            addResourceReferenceValues(facet, element, prefix, resourceType, resourcePackage, result, true);
          }
          else if (recommendedTypes.contains(resourceType) &&
                   (filteringSet == null || filteringSet.contains(resourceType))) {
            result.add(ResourceValue.literal(typePrefix));
          }
        }
      }
    }
    if (myAllowAttributeReferences) {
      completeAttributeReferences(value, facet, result);
    }
    final ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

    if (additionalConverter != null) {
      for (String variant : additionalConverter.getVariants(context)) {
        result.add(ResourceValue.literal(variant));
      }
    }
    return result;
  }

  private void addVariantsForIdDeclaration(Set<ResourceValue> result, AndroidFacet facet, char prefix, String value) {
    for (String name : ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getIds(false)) {
      final ResourceValue ref = referenceTo(prefix, "+id", null, name, true);

      if (!value.startsWith(doToString(ref))) {
        result.add(ref);
      }
    }
  }

  private static void completeAttributeReferences(String value, AndroidFacet facet, Set<ResourceValue> result) {
    if (StringUtil.startsWith(value, "?attr/")) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, true);
    }
    else if (StringUtil.startsWith(value, "?android:attr/")) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, SYSTEM_RESOURCE_PACKAGE, result, true);
    }
    else if (StringUtil.startsWithChar(value, '?')) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, false);
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, SYSTEM_RESOURCE_PACKAGE, result, false);
      result.add(ResourceValue.literal("?attr/"));
      result.add(ResourceValue.literal("?android:attr/"));
    }
  }

  @NotNull
  public static Set<ResourceType> getResourceTypesInCurrentModule(@NotNull AndroidFacet facet) {
    Set<ResourceType> result = EnumSet.noneOf(ResourceType.class);
    AppResourceRepository resourceRepository = AppResourceRepository.getOrCreateInstance(facet);
    for (ResourceType type : ResourceType.values()) {
      if (resourceRepository.hasResourcesOfType(type)) {
        if (type == ResourceType.DECLARE_STYLEABLE) {
          // The ResourceRepository maps tend to hold DECLARE_STYLEABLE, but not STYLEABLE. However, these types are
          // used for R inner classes, and declare-styleable isn't a valid inner class name, so convert to styleable.
          result.add(ResourceType.STYLEABLE);
        } else {
          result.add(type);
        }
      }
    }
    return result;
  }

  @NotNull
  private static String getTypePrefix(String resourcePackage, String type) {
    String typePart = type + '/';
    return getPackagePrefix(resourcePackage, true) + typePart;
  }

  @NotNull
  private Set<ResourceType> getResourceTypes(ConvertContext context) {
    return getResourceTypes(context.getInvocationElement());
  }

  @NotNull
  public Set<ResourceType> getResourceTypes(@NotNull DomElement element) {
    AndroidResourceType resourceType = element.getAnnotation(AndroidResourceType.class);
    Set<ResourceType> types = EnumSet.copyOf(myResourceTypes);
    if (resourceType != null) {
      String s = resourceType.value();
      if (s != null) {
        ResourceType t = ResourceType.getEnum(s);
        if (t != null) {
          types.add(t);
        }
      }
    }
    if (types.isEmpty()) {
      return VALUE_RESOURCE_TYPES;
    }
    else if (types.contains(ResourceType.DRAWABLE)) {
      types.add(ResourceType.COLOR);
    }
    if (StudioFlags.NELE_SAMPLE_DATA.get() && TOOLS_URI.equals(element.getXmlElementNamespace())) {
      // For tools: attributes, we also add the mock types
      types.add(ResourceType.SAMPLE_DATA);
    }
    return types;
  }

  private static void addResourceReferenceValues(AndroidFacet facet,
                                                 @Nullable XmlElement element,
                                                 char prefix,
                                                 ResourceType type,
                                                 @Nullable String resPackage,
                                                 Collection<ResourceValue> result,
                                                 boolean explicitResourceType) {
    PsiFile file = element != null ? element.getContainingFile() : null;
    Collection<String> names =
      type == ResourceType.ID && resPackage == null && file != null && isNonValuesResourceFile(file) ?
      ResourceHelper.findIdsInFile(file) : findResourceNames(facet, type, resPackage);
    String typeName = type.getName();
    for (String name : names) {
      result.add(referenceTo(prefix, typeName, resPackage, name, explicitResourceType));
    }
  }

  private static boolean isNonValuesResourceFile(@NotNull PsiFile file) {
    ResourceFolderType resourceType = ResourceHelper.getFolderType(file.getOriginalFile());
    return resourceType != null && resourceType != ResourceFolderType.VALUES;
  }

  private static Collection<String> findResourceNames(@NotNull AndroidFacet facet,
                                                      @NotNull ResourceType type,
                                                      @Nullable String resPackage) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(resPackage);
    if (manager == null) {
      return Collections.emptySet();
    }
    else {
      return manager.getResourceNames(type, true);
    }
  }

  private static ResourceValue referenceTo(char prefix, String type, String resPackage, String name, boolean explicitResourceType) {
    if (ResourceType.SAMPLE_DATA.getName().equals(type)) {
      // TODO(namespaces): Handling of namespaces for SAMPLE_DATA until namespaces are fully supported across the resources stack.
      List<String> sampleDataResource = Splitter.on(':')
        .trimResults()
        .omitEmptyStrings()
        .limit(2)
        .splitToList(name);

      if (resPackage == null && sampleDataResource.size() == 2) {
        resPackage = sampleDataResource.get(0);
        name = sampleDataResource.get(1);
      }
    }

    return ResourceValue.referenceTo(prefix, resPackage, explicitResourceType ? type : null, name);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    if (s == null || s.isEmpty()) {
      return "Missing value";
    }

    final ResourceValue parsed = ResourceValue.parse(s, true, myWithPrefix, false);

    if (parsed == null || !parsed.isReference()) {
      final ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

      if (myResourceTypes.contains(ResourceType.STRING)) {
        // Anything is allowed
        return null;
      }

      if (additionalConverter != null) {
        return additionalConverter.getErrorMessage(s, context);
      }
    } else {
      String errorMessage = parsed.getErrorMessage();
      if (errorMessage != null) {
        return errorMessage;
      }
    }

    return super.getErrorMessage(s, context);
  }

  /**
   * Data class that contains all required information to show on-completion documentation.
   */
  public static class DocumentationHolder {
    /**
     * Value being completed
     */
    private final @NotNull String myValue;

    /**
     * Documentation associated with that value
     */
    private final @NotNull String myDocumentation;

    public DocumentationHolder(@NotNull String value, @NotNull String documentation) {
      myValue = value;
      myDocumentation = documentation;
    }

    public @NotNull String getValue() {
      return myValue;
    }

    public @NotNull String getDocumentation() {
      return myDocumentation;
    }
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(ResourceValue resourceValue) {
    final String value = resourceValue.toString();

    boolean deprecated = false;
    String doc = null;
    if (myAttributeDefinition != null) {
      doc = myAttributeDefinition.getValueDoc(value);
      deprecated = myAttributeDefinition.isValueDeprecated(value);
    }

    LookupElementBuilder builder;
    if (doc == null) {
      builder = LookupElementBuilder.create(value);
    } else {
      builder = LookupElementBuilder.create(new DocumentationHolder(value, doc.trim()), value);
    }

    builder = builder.withCaseSensitivity(true).withStrikeoutness(deprecated);
    final String resourceName = resourceValue.getResourceName();
    if (resourceName != null) {
      builder = builder.withLookupString(resourceName);
    }

    final int priority;
    if (deprecated) {
      // Show deprecated values in the end of the autocompletion list
      priority = 0;
    } else if (TOP_PRIORITY_VALUES.contains(value)) {
      // http://b.android.com/189164
      // match_parent and wrap_content are shown higher in the list of autocompletions, if they're available
      priority = 2;
    } else {
      priority = 1;
    }

    return PrioritizedLookupElement.withPriority(builder, priority);
  }

  @Override
  public ResourceValue fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    if (DataBindingUtil.isBindingExpression(s)) return ResourceValue.INVALID;
    ResourceValue parsed = ResourceValue.parse(s, true, myWithPrefix, true);
    final ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

    if (parsed == null || !parsed.isReference()) {
      if (additionalConverter != null) {
        String value = additionalConverter.fromString(s, context);
        if (value != null) {
          return ResourceValue.literal(value);
        }
        else if (!myAdditionalConverterSoft) {
          return null;
        }
      }
      else if (!myAllowLiterals) {
        return null;
      }
    }
    if (parsed != null) {
      final String resType = parsed.getResourceType();

      if (parsed.getPrefix() == '?') {
        if (!myAllowAttributeReferences) {
          return null;
        }
        if (resType == null) {
          parsed.setResourceType(ResourceType.ATTR.getName());
        }
        else if (!ResourceType.ATTR.getName().equals(resType)) {
          return null;
        }
      }
      else if (resType == null) {
        if (parsed.isReference()) {
          if (myWithExplicitResourceType && !NULL_RESOURCE.equals(s)) {
            return null;
          }
          if (myResourceTypes.size() == 1) {
            parsed.setResourceType(myResourceTypes.iterator().next().getName());
          }
        }
        else {
          Set<ResourceType> types = getResourceTypes(context);
          if (types.contains(ResourceType.BOOL) && types.size() < VALUE_RESOURCE_TYPES.size()) {
            // For a boolean we *only* accept true or false if it's not a resource reference
            // (We're checking  VALUE_RESOURCE_TYPES.size above since for properties with
            // *unknown type* we're including all resource types, and we don't want to start
            // flagging colors (#ff00ff) as unresolved etc.
            if (!(VALUE_TRUE.equals(s) || VALUE_FALSE.equals(s))) {
              return null;
            }
          }
        }
      }
    }
    return parsed;
  }

  @Nullable
  private ResolvingConverter<String> getAdditionalConverter(ConvertContext context) {
    if (myAdditionalConverter != null) {
      return myAdditionalConverter;
    }

    final AdditionalConverter additionalConverterAnnotation =
      context.getInvocationElement().getAnnotation(AdditionalConverter.class);

    if (additionalConverterAnnotation != null) {
      final Class<? extends ResolvingConverter> converterClass = additionalConverterAnnotation.value();

      if (converterClass != null) {
        final ConverterManager converterManager = ServiceManager.getService(ConverterManager.class);
        //noinspection unchecked
        return (ResolvingConverter<String>)converterManager.getConverterInstance(converterClass);
      }
    }
    return null;
  }

  @Override
  public String toString(@Nullable ResourceValue element, ConvertContext context) {
    return doToString(element);
  }

  private String doToString(ResourceValue element) {
    if (element == null) {
      return null;
    }
    if (myWithExplicitResourceType || !element.isReference()) {
      return element.toString();
    }
    return ResourceValue.referenceTo(element.getPrefix(), element.getNamespace(), null,
                                     element.getResourceName()).toString();
  }

  @Override
  public LocalQuickFix[] getQuickFixes(ConvertContext context) {
    AndroidFacet facet = AndroidFacet.getInstance(context);
    if (facet != null) {
      final DomElement domElement = context.getInvocationElement();

      if (domElement instanceof GenericDomValue) {
        final String value = ((GenericDomValue)domElement).getStringValue();

        if (value != null) {
          ResourceValue resourceValue = ResourceValue.parse(value, false, myWithPrefix, true);
          if (resourceValue != null) {
            String aPackage = resourceValue.getNamespace();
            ResourceType resType = resourceValue.getType();
            if (resType == null && myResourceTypes.size() == 1) {
              resType = myResourceTypes.iterator().next();
            }
            final String resourceName = resourceValue.getResourceName();
            if (aPackage == null &&
                resType != null &&
                resourceName != null &&
                AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
              final List<LocalQuickFix> fixes = new ArrayList<>();

              ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(resType);
              if (folderType != null) {
                fixes.add(new CreateFileResourceQuickFix(facet, folderType, resourceName, context.getFile(), false));
              }
              if (VALUE_RESOURCE_TYPES.contains(resType) && resType != ResourceType.LAYOUT) { // layouts: aliases only
                fixes.add(new CreateValueResourceQuickFix(facet, resType, resourceName, context.getFile(), false));
              }
              return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
            }
          }
        }
      }
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiReference[] createReferences(GenericDomValue<ResourceValue> value, PsiElement element, ConvertContext context) {
    if (NULL_RESOURCE.equals(value.getStringValue())) {
      return PsiReference.EMPTY_ARRAY;
    }

    Module module = context.getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        ResourceValue resValue = value.getValue();
        if (resValue != null && resValue.isReference()) {
          String resType = resValue.getResourceType();
          if (resType == null) {
            return PsiReference.EMPTY_ARRAY;
          }

          // Don't treat "+id" as a reference if it is actually defining an id locally; e.g.
          //    android:layout_alignLeft="@+id/foo"
          // is a reference to R.id.foo, but
          //    android:id="@+id/foo"
          // is not; it's the place we're defining it.
          if (resValue.getNamespace() == null && "+id".equals(resType)
              && element != null && element.getParent() instanceof XmlAttribute) {
            XmlAttribute attribute = (XmlAttribute)element.getParent();
            if (ATTR_ID.equals(attribute.getLocalName()) && ANDROID_URI.equals(attribute.getNamespace())) {
              // When defining an id, don't point to another reference
              // TODO: Unless you use @id instead of @+id!
              return PsiReference.EMPTY_ARRAY;
            }
          }

          return new PsiReference[]{new AndroidResourceReference(value, facet, resValue, null)};
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public String getDocumentation(@NotNull String value) {
    return myAdditionalConverter instanceof AttributeValueDocumentationProvider
           ? ((AttributeValueDocumentationProvider)myAdditionalConverter).getDocumentation(value)
           : null;
  }
}
