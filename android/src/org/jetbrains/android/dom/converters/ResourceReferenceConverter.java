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

import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.NULL_RESOURCE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.tools.idea.res.IdeResourcesUtil.VALUE_RESOURCE_TYPES;
import static com.google.common.base.MoreObjects.firstNonNull;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceNamespaceContext;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.utils.DataBindingUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ConverterManager;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.dom.AdditionalConverter;
import org.jetbrains.android.dom.AndroidResourceType;
import com.android.tools.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.drawable.DrawableStateListItem;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private boolean myIncludeDynamicFeatures = false;
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

  public void setIncludeDynamicFeatures(boolean includeDynamicFeatures) {
    myIncludeDynamicFeatures = includeDynamicFeatures;
  }

  public boolean getIncludeDynamicFeatures() {
    return myIncludeDynamicFeatures;
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
        ResourceNamespaceContext namespacesContext = IdeResourcesUtil.getNamespacesContext(element);
        namespacePrefix = matcher.group(1);
        if (namespacesContext != null) {
          namespace = ResourceNamespace.fromNamespacePrefix(namespacePrefix,
                                                            namespacesContext.getCurrentNs(),
                                                            namespacesContext.getResolver());
        } else {
          namespace = ResourceNamespace.fromPackageName(namespacePrefix);
        }
      }
      else if (StudioFlags.COLLAPSE_ANDROID_NAMESPACE.get()) {
        // We don't offer framework resources in completion, unless the string already starts with the framework namespace. But we do offer
        // the right prefix, which will cause the framework resources to show up as follow-up completion. These variants are later handled
        // in createLookupElement below.
        ResourceNamespace.Resolver resolver = IdeResourcesUtil.getNamespaceResolver(element);
        String frameworkPrefix = firstNonNull(resolver.uriToPrefix(ResourceNamespace.ANDROID.getXmlNamespaceUri()),
                                              ResourceNamespace.ANDROID.getPackageName());
        result.add(ResourceValue.literal(myWithPrefix || startsWithRefChar
                                         ? '@' + frameworkPrefix + ':'
                                         : frameworkPrefix + ':'));
      }
      char prefix = myWithPrefix || startsWithRefChar ? '@' : 0;

      if (value.startsWith(NEW_ID_PREFIX)) {
        addVariantsForIdDeclaration(context, facet, prefix, value, result);
      }

      if (myExpandedCompletionSuggestion) {
        // We will add the resource type (e.g. @style/) if the current value starts like a reference using @
        boolean explicitResourceType = startsWithRefChar || myWithExplicitResourceType;
        for (ResourceType type : recommendedTypes) {
          // If getResourceTypes decided SAMPLE_DATA belongs here, then this is one of the few exceptions where it can be referenced.
          if (type.getCanBeReferenced() || type == ResourceType.SAMPLE_DATA) {
            addResourceReferenceValues(facet, element, prefix, type, namespace, result, explicitResourceType, myIncludeDynamicFeatures);
          }
        }
      }
      else {
        Set<ResourceType> filteringSet =
            namespace == ResourceNamespace.ANDROID ? EnumSet.allOf(ResourceType.class) : getResourceTypesInCurrentModule(facet);

        for (ResourceType resourceType : ResourceType.values()) {
          if (!resourceType.getCanBeReferenced()) {
            continue;
          }

          String typePrefix = getTypePrefix(namespacePrefix, resourceType);
          if (value.startsWith(typePrefix)) {
            addResourceReferenceValues(facet, element, prefix, resourceType, namespace, result, true, myIncludeDynamicFeatures);
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
    ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

    if (additionalConverter != null) {
      for (String variant : additionalConverter.getVariants(context)) {
        result.add(ResourceValue.literal(variant));
      }
    }
    return result;
  }

  private void addVariantsForIdDeclaration(@NotNull ConvertContext context, @NotNull AndroidFacet facet, char prefix, @NotNull String value,
                                           @NotNull Set<ResourceValue> result) {
    ResourceNamespace namespace = ResourceNamespace.TODO();

    // Find matching ID resource references in the current file.
    XmlFile file = context.getFile();
    file.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue attributeValue) {
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
    Collection<String> ids = StudioResourceRepositoryManager.getAppResources(facet).getResources(namespace, ResourceType.ID).keySet();
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
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, true, false);
    }
    else if (StringUtil.startsWith(value, "?android:attr/")) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, ResourceNamespace.ANDROID, result, true, false);
    }
    else if (StringUtil.startsWithChar(value, '?')) {
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, null, result, false, false);
      addResourceReferenceValues(facet, null, '?', ResourceType.ATTR, ResourceNamespace.ANDROID, result, false, false);
      result.add(ResourceValue.literal("?attr/"));
      result.add(ResourceValue.literal("?android:attr/"));
    }
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element,
                               String stringValue,
                               @Nullable ResourceValue resolveResult,
                               ConvertContext context) {
    if (element instanceof ResourceReferencePsiElement) {
      ResourceReference reference = ((ResourceReferencePsiElement)element).getResourceReference();
      XmlElement xmlElement = context.getXmlElement();
      if (xmlElement != null && resolveResult != null) {
        ResourceNamespace resolvedNamespace = IdeResourcesUtil.resolveResourceNamespace(xmlElement, resolveResult.getPackage());
        return reference.getNamespace().equals(resolvedNamespace) &&
               reference.getResourceType().equals(resolveResult.getType()) &&
               reference.getName().equals(resolveResult.getResourceName());
      }
    }
    return super.isReferenceTo(element, stringValue, resolveResult, context);
  }

  @NotNull
  public static Set<ResourceType> getResourceTypesInCurrentModule(@NotNull AndroidFacet facet) {
    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(facet);
    LocalResourceRepository repository = repositoryManager.getAppResources();
    return repository.getResourceTypes(repositoryManager.getNamespace());
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
      ResourceType t = ResourceType.fromClassName(s);
      if (t != null) {
        types.add(t);
      }
    }
    if (types.isEmpty()) {
      return VALUE_RESOURCE_TYPES;
    }
    else if (types.contains(ResourceType.DRAWABLE)) {
      types.add(ResourceType.COLOR);
      if (element.getParent() instanceof DrawableStateListItem) {
        types.add(ResourceType.MIPMAP);
      }
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
                                                 boolean explicitResourceType,
                                                 boolean includeDynamicFeatures) {
    PsiFile file = element != null ? element.getContainingFile() : null;

    if (type == ResourceType.ID && onlyNamespace != ResourceNamespace.ANDROID && file != null && isNonValuesResourceFile(file)) {
      for (ResourceUrl url : IdeResourcesUtil.findIdUrlsInFile(file)) {
        result.add(referenceTo(prefix, type.getName(), url.namespace, url.name, explicitResourceType));
      }
    }
    else {
      StudioResourceRepositoryManager repoManager = StudioResourceRepositoryManager.getInstance(facet);
      LocalResourceRepository appResources = repoManager.getAppResources();

      if (onlyNamespace == ResourceNamespace.ANDROID || (onlyNamespace == null && !StudioFlags.COLLAPSE_ANDROID_NAMESPACE.get())) {
        ResourceRepository frameworkResources = repoManager.getFrameworkResources(ImmutableSet.of());
        if (frameworkResources != null) {
          addResourceReferenceValuesFromRepo(frameworkResources, repoManager, element, prefix, type,
                                             ResourceNamespace.ANDROID, result, explicitResourceType);
        }
      }

      if (onlyNamespace == null) {
        for (ResourceNamespace namespace : appResources.getNamespaces()) {
          addResourceReferenceValuesFromRepo(appResources, repoManager, element, prefix, type, namespace, result,
                                             explicitResourceType);
        }
      }
      else {
        addResourceReferenceValuesFromRepo(appResources, repoManager, element, prefix, type, onlyNamespace, result,
                                           explicitResourceType);
      }
      if (includeDynamicFeatures) {
        addResourceReferenceValuesFromDynamicFeatures(element, prefix, type, onlyNamespace, result);
      }
    }
  }

  private static void addResourceReferenceValuesFromDynamicFeatures(@Nullable XmlElement element,
                                                                    char prefix,
                                                                    @Nullable ResourceType type,
                                                                    @Nullable ResourceNamespace onlyNamespace,
                                                                    @NotNull Collection<ResourceValue> result) {
    ResourceNamespace namespace = onlyNamespace != null ? onlyNamespace : ResourceNamespace.RES_AUTO;
    if (element == null || type == null) {
      return;
    }
    AndroidModuleSystem androidModuleSystem = ProjectSystemUtil.getModuleSystem(element);
    if (androidModuleSystem == null) {
      return;
    }

    ResourceNamespace.Resolver resolver = firstNonNull(
      IdeResourcesUtil.getNamespaceResolver(element),
      ResourceNamespace.Resolver.EMPTY_RESOLVER);
    // Find the short prefix once for all items.
    String namespacePrefix = resolver.uriToPrefix(namespace.getXmlNamespaceUri());
    List<Module> modules = androidModuleSystem.getDynamicFeatureModules();
    for (Module module : modules) {
      LocalResourceRepository moduleResources = StudioResourceRepositoryManager.getModuleResources(module);
      if (moduleResources == null) {
        continue;
      }
      Set<String> resourceNames = moduleResources.getResourceNames(namespace, type);
      for (String name : resourceNames) {
        result.add(referenceTo(prefix, type.getName(), namespacePrefix, name, true));
      }
    }
  }

  private static void addResourceReferenceValuesFromRepo(ResourceRepository repo,
                                                         StudioResourceRepositoryManager repoManager,
                                                         @Nullable XmlElement element,
                                                         char prefix,
                                                         ResourceType type,
                                                         @NotNull ResourceNamespace onlyNamespace,
                                                         Collection<ResourceValue> result,
                                                         boolean explicitResourceType) {
    Collection<String> names = IdeResourcesUtil.getResourceItems(repo, onlyNamespace, type, ResourceVisibility.PUBLIC);

    ResourceNamespace.Resolver resolver = ResourceNamespace.Resolver.EMPTY_RESOLVER;
    if (element != null) {
      resolver = firstNonNull(IdeResourcesUtil.getNamespaceResolver(element), resolver);
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
    ResourceFolderType resourceType = IdeResourcesUtil.getFolderType(file.getOriginalFile());
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

    ResourceValue parsed = ResourceValue.parse(s, true, myWithPrefix, false);

    if (parsed != null && parsed.isReference()) {
      String errorMessage = parsed.getErrorMessage();
      if (errorMessage != null) {
        return "Invalid resource reference - " + StringUtil.decapitalize(errorMessage);
      }
    }
    else {
      if (myResourceTypes.contains(ResourceType.STRING)) {
        // Anything is allowed
        return null;
      }

      ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);
      if (additionalConverter != null) {
        return additionalConverter.getErrorMessage(s, context);
      }
    }

    return super.getErrorMessage(s, context);
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(ResourceValue resourceValue) {
    String value = resourceValue.toString();

    boolean deprecated = false;
    if (myAttributeDefinition != null) {
      deprecated = myAttributeDefinition.isValueDeprecated(value);
    }

    LookupElementBuilder builder = LookupElementBuilder.create(value);

    builder = builder.withCaseSensitivity(true).withStrikeoutness(deprecated);
    String resourceName = resourceValue.getResourceName();
    if (resourceName != null) {
      builder = builder.withLookupString(resourceName);
    }
    else {
      if (isNamespaceLiteral(resourceValue.getValue())) {
        // This is a "fake" ResourceValue to offer just a namespace prefix as completion.
        builder = builder.withInsertHandler((context, item) -> context.setLaterRunnable(() -> {
          // This is similar to JavaClassNameInsertHandler#handleInsert.
          new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(context.getProject(), context.getEditor());
        }));
      }
    }

    int priority;
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

  public boolean isNamespaceLiteral(@Nullable String value) {
    return value != null && value.charAt(value.length() - 1) == ':' && value.indexOf('/') == -1;
  }

  @Override
  public ResourceValue fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    if (DataBindingUtils.isBindingExpression(s)) return ResourceValue.INVALID;
    ResourceValue parsed = ResourceValue.parse(s, true, myWithPrefix, true);
    ResolvingConverter<String> additionalConverter = getAdditionalConverter(context);

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
      String resType = parsed.getResourceType();

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
          if (types.size() == 1 && types.contains(ResourceType.BOOL)) {
            // For a boolean we *only* accept true or false if it's not a resource reference
            // We're only performing this check if the eligible resource type is only boolean
            // as we don't want to start flagging resource values with multiple resource type
            // formats eg. "string|boolean|color" as unresolved etc.
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

    AdditionalConverter additionalConverterAnnotation =
      context.getInvocationElement().getAnnotation(AdditionalConverter.class);

    if (additionalConverterAnnotation != null) {
      Class<? extends ResolvingConverter> converterClass = additionalConverterAnnotation.value();

      ConverterManager converterManager = ApplicationManager.getApplication().getService(ConverterManager.class);
      //noinspection unchecked
      return (ResolvingConverter<String>)converterManager.getConverterInstance(converterClass);
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
      DomElement domElement = context.getInvocationElement();

      if (domElement instanceof GenericDomValue) {
        String value = ((GenericDomValue)domElement).getStringValue();

        if (value != null) {
          ResourceValue resourceValue = ResourceValue.parse(value, false, myWithPrefix, true);
          if (resourceValue != null) {
            String aPackage = resourceValue.getPackage();
            ResourceType resType = resourceValue.getType();
            if (resType == null && myResourceTypes.size() == 1) {
              resType = myResourceTypes.iterator().next();
            }
            String resourceName = resourceValue.getResourceName();
            if (aPackage == null &&
                resType != null &&
                resourceName != null &&
                IdeResourcesUtil.isCorrectAndroidResourceName(resourceName)) {
              List<LocalQuickFix> fixes = new ArrayList<>();

              ResourceFolderType folderType = FolderTypeRelationship.getNonValuesRelatedFolder(resType);
              if (folderType != null) {
                fixes.add(new CreateFileResourceQuickFix(facet, folderType, resourceName, context.getFile(), false));
              }
              if (VALUE_RESOURCE_TYPES.contains(resType) && resType != ResourceType.LAYOUT) { // layouts: aliases only
                fixes.add(new CreateValueResourceQuickFix(facet, resType, resourceName, context.getFile(), true));
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

    AndroidResourceReference resourceReference = new AndroidResourceReference(value, facet, resValue, myIncludeDynamicFeatures);
    if (!StringUtil.isEmpty(resValue.getPackage())) {
      ResourceNamespaceReference namespaceReference = new ResourceNamespaceReference(value, resValue);
      return new PsiReference[] {namespaceReference, resourceReference};
    }

    return new PsiReference[] {resourceReference};
  }

  @Override
  public String getDocumentation(@NotNull String value) {
    if (myAttributeDefinition != null) {
      // Custom documentation for enums and flags of attr resources.
      String description = myAttributeDefinition.getValueDescription(value);
      if (description != null) {
        return description;
      }
    }
    return myAdditionalConverter instanceof AttributeValueDocumentationProvider
           ? ((AttributeValueDocumentationProvider)myAdditionalConverter).getDocumentation(value)
           : null;
  }
}
