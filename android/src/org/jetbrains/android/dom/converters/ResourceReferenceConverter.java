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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNamespaceContext;
import com.android.tools.idea.res.ResourceRepositoryManager;
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
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.AdditionalConverter;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.google.common.base.MoreObjects.firstNonNull;
import static org.jetbrains.android.util.AndroidResourceUtil.VALUE_RESOURCE_TYPES;

/**
 * @author yole
 */
public class ResourceReferenceConverter extends ResolvingConverter<ResourceValue>
  implements CustomReferenceConverter<ResourceValue>, AttributeValueDocumentationProvider {

  private static final Pattern NAMESPACE_COLON = Pattern.compile("^((?:\\w|\\.)+):.*");
  private static final Pattern PREFIX_NAMESPACE_COLON = Pattern.compile("^@((?:\\w|\\.)+):.*");

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
    Module module = context.getModule();
    if (module == null || module.isDisposed()) return Collections.emptySet();
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) return Collections.emptySet();

    Set<ResourceValue> result = new HashSet<>();
    Set<ResourceType> recommendedTypes = getResourceTypes(context);

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
      ResourceNamespace namespace = null;
      String namespacePrefix = null;
      // Retrieve the system prefix depending on the prefix settings ("@android:" or "android:")
      Matcher matcher = (myWithPrefix ? PREFIX_NAMESPACE_COLON : NAMESPACE_COLON).matcher(value);
      if (matcher.matches()) {
        ResourceNamespaceContext namespacesContext = ResourceHelper.getNamespacesContext(element);
        namespacePrefix = matcher.group(1);
        if (namespacesContext != null) {
          namespace = ResourceNamespace.fromNamespacePrefix(namespacePrefix,
                                                            namespacesContext.getCurrentNs(),
                                                            namespacesContext.getResolver());
        } else {
          namespace = ResourceNamespace.fromPackageName(namespacePrefix);
        }
      }
      else {
        // We don't offer framework resources in completion, unless the string already starts with the framework namespace. But we do offer
        // the right prefix, which will cause the framework resources to show up as follow-up completion.
        ResourceNamespace.Resolver resolver = ResourceHelper.getNamespaceResolver(element);
        String frameworkPrefix = firstNonNull(resolver.uriToPrefix(ResourceNamespace.ANDROID.getXmlNamespaceUri()),
                                              ResourceNamespace.ANDROID.getPackageName());
        result.add(ResourceValue.literal(myWithPrefix || startsWithRefChar
                                         ? '@' + frameworkPrefix + ':'
                                         : frameworkPrefix + ':'));
      }
      final char prefix = myWithPrefix || startsWithRefChar ? '@' : 0;

      if (value.startsWith(NEW_ID_PREFIX)) {
        addVariantsForIdDeclaration(context, facet, prefix, value, result);
      }

      if (recommendedTypes.size() >= 1 && myExpandedCompletionSuggestion) {
        // We will add the resource type (e.g. @style/) if the current value starts like a reference using @
        final boolean explicitResourceType = startsWithRefChar || myWithExplicitResourceType;
        for (final ResourceType type : recommendedTypes) {
          addResourceReferenceValues(facet, element, prefix, type, namespace, result, explicitResourceType);
        }
      }
      else {
        EnumSet<ResourceType> filteringSet = namespace == ResourceNamespace.ANDROID
                                                   ? EnumSet.allOf(ResourceType.class)
                                                   : getResourceTypesInCurrentModule(facet);

        for (ResourceType resourceType : ResourceType.values()) {
          String typePrefix = getTypePrefix(namespacePrefix, resourceType);
          if (value.startsWith(typePrefix)) {
            addResourceReferenceValues(facet, element, prefix, resourceType, namespace, result, true);
          }
          else if (recommendedTypes.contains(resourceType) && filteringSet.contains(resourceType)) {
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

  private void addVariantsForIdDeclaration(@NotNull ConvertContext context, @NotNull AndroidFacet facet, char prefix, @NotNull String value,
                                           @NotNull Set<ResourceValue> result) {
    ResourceNamespace namespace = ResourceNamespace.TODO;

    // Find matching ID resource references in the current file.
    XmlFile file = context.getFile();
    file.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
        String valueText = attributeValue.getValue();
        if (valueText != null && valueText.startsWith(ID_PREFIX) && valueText.length() > ID_PREFIX.length()) {
          String name = valueText.substring(ID_PREFIX.length());
          ResourceValue ref = referenceTo(prefix, "+id", namespace.getPackageName(), name, true);
          if (!value.startsWith(doToString(ref))) {
            result.add(ref);
          }
        }
      }
    });

    // Find matching ID resource declarations.
    Collection<String> ids = AppResourceRepository.getOrCreateInstance(facet).getItemsOfType(namespace, ResourceType.ID);
    for (String name : ids) {
      ResourceValue ref = referenceTo(prefix, "+id", namespace.getPackageName(), name, true);
      if (!value.startsWith(doToString(ref))) {
        result.add(ref);
      }
    }
  }

  private static void completeAttributeReferences(String value, AndroidFacet facet, Set<ResourceValue> result) {
    // TODO: namespaces
    if (StringUtil.startsWith(value, "?attr/")) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, true);
    }
    else if (StringUtil.startsWith(value, "?android:attr/")) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, ResourceNamespace.ANDROID, result, true);
    }
    else if (StringUtil.startsWithChar(value, '?')) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, false);
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, ResourceNamespace.ANDROID, result, false);
      result.add(ResourceValue.literal("?attr/"));
      result.add(ResourceValue.literal("?android:attr/"));
    }
  }

  @NotNull
  public static EnumSet<ResourceType> getResourceTypesInCurrentModule(@NotNull AndroidFacet facet) {
    EnumSet<ResourceType> result = EnumSet.noneOf(ResourceType.class);
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
  private String getTypePrefix(@Nullable String namespacePrefix, @NotNull ResourceType type) {
    StringBuilder sb = new StringBuilder();
    if (myWithPrefix) {
      sb.append('@');
    }
    if (namespacePrefix != null) {
      sb.append(namespacePrefix).append(':');
    }
    sb.append(type.getName()).append('/');
    return sb.toString();
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
    if (TOOLS_URI.equals(element.getXmlElementNamespace())) {
      // For tools: attributes, we also add the mock types
      types.add(ResourceType.SAMPLE_DATA);
    }
    return types;
  }

  private static void addResourceReferenceValues(AndroidFacet facet,
                                                 @Nullable XmlElement element,
                                                 char prefix,
                                                 ResourceType type,
                                                 @Nullable ResourceNamespace onlyNamespace,
                                                 Collection<ResourceValue> result,
                                                 boolean explicitResourceType) {
    PsiFile file = element != null ? element.getContainingFile() : null;

    if (type == ResourceType.ID && onlyNamespace != ResourceNamespace.ANDROID && file != null && isNonValuesResourceFile(file)) {
      // TODO: namespaces
      for (String id : ResourceHelper.findIdsInFile(file)) {
        result.add(referenceTo(prefix, type.getName(), null, id, explicitResourceType));
      }
    }
    else {
      ResourceRepositoryManager repoManager = ResourceRepositoryManager.getOrCreateInstance(facet);
      ResourceVisibilityLookup visibilityLookup = repoManager.getResourceVisibility();
      if (onlyNamespace == ResourceNamespace.ANDROID) {
        AbstractResourceRepository frameworkResources = repoManager.getFrameworkResources(false);
        if (frameworkResources == null) {
          return;
        }
        addResourceReferenceValuesFromRepo(frameworkResources, repoManager, visibilityLookup, element, prefix, type, onlyNamespace, result,
                                           explicitResourceType);
      }
      else {
        AppResourceRepository appResources = repoManager.getAppResources(true);

        if (onlyNamespace != null) {
          addResourceReferenceValuesFromRepo(appResources, repoManager, visibilityLookup, element, prefix, type, onlyNamespace, result,
                                             explicitResourceType);
        } else {
          for (ResourceNamespace namespace : appResources.getNamespaces()) {
            addResourceReferenceValuesFromRepo(appResources, repoManager, visibilityLookup, element, prefix, type, namespace, result,
                                               explicitResourceType);
          }
        }
      }
    }
  }

  private static void addResourceReferenceValuesFromRepo(AbstractResourceRepository repo,
                                                         ResourceRepositoryManager repoManager,
                                                         ResourceVisibilityLookup visibilityLookup,
                                                         @Nullable XmlElement element,
                                                         char prefix,
                                                         ResourceType type,
                                                         @NotNull ResourceNamespace onlyNamespace,
                                                         Collection<ResourceValue> result,
                                                         boolean explicitResourceType) {
    Collection<String> names =
      ResourceHelper.getResourceItems(repo, onlyNamespace, type, visibilityLookup, ResourceVisibility.PUBLIC);

    ResourceNamespace.Resolver resolver = ResourceNamespace.Resolver.EMPTY_RESOLVER;
    if (element != null) {
      resolver = firstNonNull(ResourceHelper.getNamespaceResolver(element), resolver);
    }

    // Find the short prefix once for all items.
    String namespacePrefix =
      new ResourceReference(onlyNamespace, ResourceType.STRING, "dummy")
        .getRelativeResourceUrl(repoManager.getNamespace(), resolver)
        .namespace;

    for (String name : names) {
      result.add(referenceTo(prefix, type.getName(), namespacePrefix, name, explicitResourceType));
    }
  }

  private static boolean isNonValuesResourceFile(@NotNull PsiFile file) {
    ResourceFolderType resourceType = ResourceHelper.getFolderType(file.getOriginalFile());
    return resourceType != null && resourceType != ResourceFolderType.VALUES;
  }

  private static ResourceValue referenceTo(char prefix, String type, String resPackage, String name, boolean explicitResourceType) {
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
    return ResourceValue.referenceTo(element.getPrefix(), element.getPackage(), null,
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
            String aPackage = resourceValue.getPackage();
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
    if (module == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    ResourceValue resValue = value.getValue();
    if (resValue == null || !resValue.isReference()) {
      return PsiReference.EMPTY_ARRAY;
    }

    String resType = resValue.getResourceType();
    if (resType == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    // Don't treat "+id" as a reference if it is actually defining an id locally; e.g.
    //    android:layout_alignLeft="@+id/foo"
    // is a reference to R.id.foo, but
    //    android:id="@+id/foo"
    // is not; it's the place we're defining it.
    if (resValue.getPackage() == null && "+id".equals(resType) && element != null && element.getParent() instanceof XmlAttribute) {
      XmlAttribute attribute = (XmlAttribute)element.getParent();
      if (ATTR_ID.equals(attribute.getLocalName()) && ANDROID_URI.equals(attribute.getNamespace())) {
        // When defining an id, don't point to another reference
        return PsiReference.EMPTY_ARRAY;
      }
    }

    AndroidResourceReference resourceReference = new AndroidResourceReference(value, facet, resValue);
    if (!StringUtil.isEmpty(resValue.getPackage())) {
      ResourceNamespaceReference namespaceReference = new ResourceNamespaceReference(value, resValue);
      return new PsiReference[] {namespaceReference, resourceReference};
    }

    return new PsiReference[] {resourceReference};
  }

  @Override
  public String getDocumentation(@NotNull String value) {
    return myAdditionalConverter instanceof AttributeValueDocumentationProvider
           ? ((AttributeValueDocumentationProvider)myAdditionalConverter).getDocumentation(value)
           : null;
  }
}
