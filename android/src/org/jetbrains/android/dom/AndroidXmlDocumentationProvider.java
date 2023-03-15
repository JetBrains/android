package org.jetbrains.android.dom;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.URI_PREFIX;
import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;
import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;
import static com.intellij.psi.xml.XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
import static com.intellij.psi.xml.XmlTokenType.XML_DATA_CHARACTERS;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.android.tools.idea.javadoc.AndroidJavaDocRenderer;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.utils.Pair;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.reference.SoftReference;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.converters.AttributeValueDocumentationProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlDocumentationProvider implements DocumentationProvider {
  private static final Key<SoftReference<Map<XmlName, CachedValue<String>>>> ANDROID_ATTRIBUTE_DOCUMENTATION_CACHE_KEY =
      Key.create("ANDROID_ATTRIBUTE_DOCUMENTATION_CACHE");

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof ResourceReferencePsiElement) {
      return ((ResourceReferencePsiElement)element).getResourceReference().getResourceUrl().toString();
    }
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element instanceof ProvidedDocumentationPsiElement) {
      return ((ProvidedDocumentationPsiElement)element).getDocumentation();
    }
    if (element instanceof ResourceReferencePsiElement) {
      ResourceUrl originalUrl = originalElement != null ? ResourceUrl.parse(originalElement.getText()) : null;
      ResourceReference resourceReference = ((ResourceReferencePsiElement)element).getResourceReference();
      if (resourceReference.getResourceType().equals(ResourceType.ATTR) && originalUrl != null) {
        // This might be a theme reference, in which case we want to use the Url form the original XML element.
        return generateDoc(originalElement, originalUrl);
      } else {
        ResourceUrl resourceUrl = resourceReference.getResourceUrl();
        return generateDoc(originalElement, resourceUrl);
      }
    }
    if (element instanceof ResourceReferenceCompletionElement) {
      return getResourceDocumentation(element.getParent(), ((ResourceReferenceCompletionElement)element).myResource);
    } else if (element instanceof XmlAttributeValue) {
      return getResourceDocumentation(element, ((XmlAttributeValue)element).getValue());
    } else if (element instanceof MyDocElement) {
      return ((MyDocElement)element).getDocumentation();
    }
    if (originalElement instanceof XmlToken) {
      XmlToken token = (XmlToken)originalElement;
      if (token.getTokenType() == XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        PsiElement next = token.getNextSibling();
        if (next instanceof XmlToken) {
          token = (XmlToken)next;
        }
      } else if (token.getTokenType() == XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        PsiElement prev = token.getPrevSibling();
        if (prev instanceof XmlToken) {
          token = (XmlToken)prev;
        }
      }
      if (token.getTokenType() == XML_ATTRIBUTE_VALUE_TOKEN) {
        String documentation = getResourceDocumentation(originalElement, token.getText());
        if (documentation != null) {
          return documentation;
        }
      } else if (token.getTokenType() == XML_DATA_CHARACTERS) {
        String text = token.getText().trim();
        String documentation = getResourceDocumentation(originalElement, text);
        if (documentation != null) {
          return documentation;
        }
      }
    }

    if (element instanceof PomTargetPsiElement && originalElement != null) {
      PomTarget target = ((PomTargetPsiElement)element).getTarget();

      if (target instanceof DomAttributeChildDescription) {
        synchronized (ANDROID_ATTRIBUTE_DOCUMENTATION_CACHE_KEY) {
          return generateDocForXmlAttribute((DomAttributeChildDescription)target, originalElement);
        }
      }
    }

    return null;
  }

  @Nullable
  private static String getResourceDocumentation(PsiElement element, String value) {
    ResourceUrl url = ResourceUrl.parse(value);
    if (url != null) {
      return generateDoc(element, url);
    } else {
      XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class, false);
      XmlTag tag = null;
      // True if getting the documentation of the XML value (not the value of the name attribute)
      boolean isXmlValue = false;

      // If the XmlToken under the cursor is within the value of the XmlTag (XML_DATA_CHARACTERS),
      // get the XmlAttribute using the containing tag
      if (element instanceof XmlToken && XML_DATA_CHARACTERS.equals(((XmlToken)element).getTokenType())) {
        tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        attribute = tag == null ? null : tag.getAttribute(ATTR_NAME);
        isXmlValue = true;
      }

      if (attribute == null) {
        return null;
      }

      if (ATTR_NAME.equals(attribute.getName())) {
        tag = tag != null ? tag : attribute.getParent();
        XmlTag parentTag = tag.getParentTag();
        if (parentTag == null) {
          return null;
        }

        if (TAG_RESOURCES.equals(parentTag.getName())) {
          // Handle ID definitions, http://developer.android.com/guide/topics/resources/more-resources.html#Id
          ResourceType type = IdeResourcesUtil.getResourceTypeForResourceTag(tag);
          if (type != null) {
            return generateDoc(element, type, value, false);
          }
        }
        else if (TAG_STYLE.equals(parentTag.getName())) {
          // String used to get attribute definitions
          String targetValue = value;

          if (isXmlValue && attribute.getValue() != null) {
            // In this case, the target is the name attribute of the <item> tag, which contains the key of the attr enum
            targetValue = attribute.getValue();
          }

          targetValue = StringUtil.trimStart(targetValue, ANDROID_NS_NAME_PREFIX);

          // Handle style item definitions, http://developer.android.com/guide/topics/resources/style-resource.html
          AttributeDefinition attributeDefinition = getAttributeDefinitionForElement(element, targetValue);
          if (attributeDefinition == null) {
            return null;
          }

          // Return the doc of the value if searching for an enum value, otherwise return the doc of the enum itself
          return StringUtil.trim(isXmlValue ? attributeDefinition.getValueDescription(value) : attributeDefinition.getDescription(null));
        }
      }

      // Display documentation for enum values defined in attrs.xml file, if it's present
      if (ANDROID_URI.equals(attribute.getNamespace())) {
        AttributeDefinition attributeDefinition = getAttributeDefinitionForElement(element, attribute.getLocalName());
        if (attributeDefinition == null) {
          return null;
        }
        return StringUtil.trim(attributeDefinition.getValueDescription(value));
      }
    }
    return null;
  }

  /**
   * Try to get the attribute definition for an element using first the system resource manager and then the local one.
   * Return null if can't find definition in any resource manager.
   */
  @Nullable
  private static AttributeDefinition getAttributeDefinitionForElement(@NotNull PsiElement element, @NotNull String name) {
    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) {
      return null;
    }
    AttributeDefinitions definitions = getAttributeDefinitions(ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager());
    if (definitions == null) {
      return null;
    }
    AttributeDefinition attributeDefinition = definitions.getAttrDefByName(name);
    if (attributeDefinition == null) {
      // Try to get the attribute definition using the local resource manager instead
      definitions = getAttributeDefinitions(ModuleResourceManagers.getInstance(facet).getLocalResourceManager());
      if (definitions == null) {
        return null;
      }
      attributeDefinition = definitions.getAttrDefByName(name);
    }
    return attributeDefinition;
  }

  @Nullable
  private static AttributeDefinitions getAttributeDefinitions(@Nullable ResourceManager manager) {
    return manager == null ? null : manager.getAttributeDefinitions();
  }

  @Nullable
  private static String generateDocForXmlAttribute(@NotNull DomAttributeChildDescription description, @NotNull PsiElement originalElement) {
    XmlName xmlName = description.getXmlName();

    Map<XmlName, CachedValue<String>> cachedDocsMap =
        SoftReference.dereference(originalElement.getUserData(ANDROID_ATTRIBUTE_DOCUMENTATION_CACHE_KEY));

    if (cachedDocsMap != null) {
      CachedValue<String> cachedDoc = cachedDocsMap.get(xmlName);

      if (cachedDoc != null) {
        return cachedDoc.getValue();
      }
    }
    AndroidFacet facet = AndroidFacet.getInstance(originalElement);

    if (facet == null) {
      return null;
    }
    String localName = xmlName.getLocalName();
    String namespace = xmlName.getNamespaceKey();

    if (namespace == null) {
      return null;
    }
    if (AndroidUtils.NAMESPACE_KEY.equals(namespace)) {
      namespace = ANDROID_URI;
    }

    if (namespace.startsWith(URI_PREFIX) || namespace.equals(AUTO_URI)) {
      String finalNamespace = namespace;

      CachedValue<String> cachedValue = CachedValuesManager.getManager(originalElement.getProject()).createCachedValue(
        () -> {
          Pair<AttributeDefinition, String> pair = findAttributeDefinition(originalElement, facet, finalNamespace, localName);
          String doc = pair != null ? generateDocForXmlAttribute(pair.getFirst(), pair.getSecond()) : null;
          return CachedValueProvider.Result.create(doc, PsiModificationTracker.MODIFICATION_COUNT);
        }, false);
      if (cachedDocsMap == null) {
        cachedDocsMap = new HashMap<>();
        originalElement.putUserData(ANDROID_ATTRIBUTE_DOCUMENTATION_CACHE_KEY, new SoftReference<>(cachedDocsMap));
      }
      cachedDocsMap.put(xmlName, cachedValue);
      return cachedValue.getValue();
    }
    return null;
  }

  @Nullable
  private static Pair<AttributeDefinition, String> findAttributeDefinition(@NotNull PsiElement originalElement,
                                                                           @NotNull AndroidFacet facet,
                                                                           @NotNull String namespace,
                                                                           @NotNull String localName) {
    if (!originalElement.isValid()) {
      return null;
    }
    XmlTag parentTag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class);

    if (parentTag == null) {
      return null;
    }
    DomElement parentDomElement = DomManager.getDomManager(parentTag.getProject()).getDomElement(parentTag);

    if (!(parentDomElement instanceof AndroidDomElement)) {
      return null;
    }
    Ref<Pair<AttributeDefinition, String>> result = Ref.create();
    try {
      AttributeProcessingUtil.processAttributes((AndroidDomElement)parentDomElement, facet, false, (xn, attrDef, parentStyleableName) -> {
        if (xn.getLocalName().equals(localName) && namespace.equals(xn.getNamespaceKey())) {
          result.set(Pair.of(attrDef, parentStyleableName));
          throw new MyStopException();
        }
        return null;
      });
    }
    catch (MyStopException e) {
      // ignore
    }

    Pair<AttributeDefinition, String> pair = result.get();

    if (pair != null) {
      return pair;
    }
    AttributeDefinition attrDef = findAttributeDefinitionGlobally(facet, namespace, localName);
    return attrDef != null ? Pair.of(attrDef, (String)null) : null;
  }

  @Nullable
  private static AttributeDefinition findAttributeDefinitionGlobally(@NotNull AndroidFacet facet,
                                                                     @NotNull String namespace,
                                                                     @NotNull String localName) {
    ResourceManager resourceManager;
    if (ANDROID_URI.equals(namespace) || TOOLS_URI.equals(namespace)) {
      resourceManager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
    }
    else if (namespace.equals(AUTO_URI) || namespace.startsWith(URI_PREFIX)) {
        resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    }
    else {
      resourceManager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager();
    }

    if (resourceManager != null) {
      AttributeDefinitions attributes = resourceManager.getAttributeDefinitions();

      if (attributes != null) {
        return attributes.getAttrDefByName(localName);
      }
    }
    return null;
  }

  private static String generateDocForXmlAttribute(@NotNull AttributeDefinition definition, @Nullable String parentStyleable) {
    StringBuilder builder = new StringBuilder("<html><body>");
    Set<AttributeFormat> formats = definition.getFormats();

    if (!formats.isEmpty()) {
      builder.append("Formats: ");
      List<String> formatLabels = new ArrayList<>(formats.size());

      for (AttributeFormat format : formats) {
        formatLabels.add(StringUtil.toLowerCase(format.name()));
      }
      Collections.sort(formatLabels);

      for (int i = 0, n = formatLabels.size(); i < n; i++) {
        builder.append(formatLabels.get(i));

        if (i < n - 1) {
          builder.append(", ");
        }
      }
    }
    String[] values = definition.getValues();

    if (values.length > 0) {
      if (builder.length() > 0) {
        builder.append("<br>");
      }
      builder.append("Values: ");
      String[] sortedValues = new String[values.length];
      System.arraycopy(values, 0, sortedValues, 0, values.length);
      Arrays.sort(sortedValues);

      for (int i = 0; i < sortedValues.length; i++) {
        builder.append(sortedValues[i]);

        if (i < sortedValues.length - 1) {
          builder.append(", ");
        }
      }
    }
    // TODO(namespaces): Remove use of the deprecated method.
    String docValue = definition.getDescriptionByParentStyleableName(parentStyleable);

    if (docValue != null && !docValue.isEmpty()) {
      if (builder.length() > 0) {
        builder.append("<br><br>");
      }
      builder.append(docValue);
    }
    builder.append("</body></html>");
    return builder.toString();
  }

  @Nullable
  private static String generateDoc(PsiElement originalElement, ResourceType type, String name, boolean framework) {
    Module module = ModuleUtilCore.findModuleForPsiElement(originalElement);
    if (module == null) {
      return null;
    }

    return AndroidJavaDocRenderer.render(module, type, name, framework);
  }

  @Nullable
  private static String generateDoc(PsiElement originalElement, ResourceUrl url) {
    Module module = ModuleUtilCore.findModuleForPsiElement(originalElement);
    if (module == null) {
      return null;
    }

    return AndroidJavaDocRenderer.render(module, null, url);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(@NotNull PsiManager psiManager, @NotNull Object object, @NotNull PsiElement element) {
    if (element instanceof XmlToken) {
      element = element.getParent();
    }
    if ((!(element instanceof XmlAttributeValue) && !(element instanceof XmlToken)) || !(object instanceof String)) {
      return null;
    }
    String value = (String)object;
    PsiElement parent = element.getParent();

    if (!(parent instanceof XmlAttribute)) {
      return null;
    }
    GenericAttributeValue domValue = DomManager.getDomManager(
      parent.getProject()).getDomElement((XmlAttribute)parent);

    if (domValue == null) {
      return null;
    }
    Converter converter = domValue.getConverter();

    if ((value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF)) && !DataBindingUtil.isBindingExpression(value)) {
      return new ResourceReferenceCompletionElement(element, value);
    }

    if (converter instanceof AttributeValueDocumentationProvider) {
      return new MyDocElement((XmlAttribute)parent, value);
    }
    return null;
  }

  private static class MyDocElement extends FakePsiElement {

    XmlAttribute myParent;
    String myValue;

    private MyDocElement(@NotNull XmlAttribute parent, String value) {
      myParent = parent;
      myValue = value;
    }

    public String getDocumentation() {
      GenericAttributeValue domValue = DomManager.getDomManager(myParent.getProject()).getDomElement(myParent);
      if (domValue == null) {
        return null;
      }
      Converter converter = domValue.getConverter();
      return ((AttributeValueDocumentationProvider)converter).getDocumentation(myValue);
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }
  }

  /**
   * A {@link FakePsiElement} for a Resource Reference found in Xml completion.
   *
   * Contains the element from which the completion has been started from, and the a String taken from lookup
   * element, which is a {@link ResourceUrl}.
   */
  public static class ResourceReferenceCompletionElement extends FakePsiElement {
    final PsiElement myParent;
    final String myResource;

    private ResourceReferenceCompletionElement(@NotNull PsiElement parent, @NotNull String resource) {
      myParent = parent;
      myResource = resource;
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    public String getResource() {
      return myResource;
    }

    @Override
    public PsiFile getContainingFile() {
      return null;
    }

    @NotNull
    @Override
    public Language getLanguage() {
      return XMLLanguage.INSTANCE;
    }
  }

  private static class MyStopException extends RuntimeException {
  }
}
