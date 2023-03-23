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
package org.jetbrains.android;

import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.databinding.DataBindingAnnotationsService;
import com.android.tools.idea.lang.databinding.DataBindingCompletionUtil;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlAttributeInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.converters.DelimitedListConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.AttributeProcessingUtil;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.color.AndroidColorDomUtil;
import org.jetbrains.android.dom.converters.FlagConverter;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription;
import org.jetbrains.android.dom.layout.Data;
import org.jetbrains.android.dom.layout.DataBindingDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.raw.RawDomFileDescription;
import org.jetbrains.android.dom.transition.TransitionDomFileDescription;
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;

/**
 * {@link CompletionContributor} for Android XML files. It provides:
 * <ul>
 *   <li>root tag names
 *   <li>a fake {@link LookupElement} with just the {@code android:} namespace prefix
 *   <li>design-time attributes, e.g. {@code tools:text}
 *   <li>Data binding attributes and values of the {@code type} attribute
 *   <li>replacement {@link LookupElement}s for deprecated layout attributes
 *   <li>combinations of flag values to be used with attr's with {@code format="flags"}
 * </ul>
 */
public class AndroidXmlCompletionContributor extends CompletionContributor {

  private static final String LAYOUT_ATTRIBUTE_PREFIX = "layout_";

  private static final String NAMESPACE_PREFIX = "xmlns";
  private static final String[] AVAILABLE_NAMESPACES = new String[] {
    "android=\"http://schemas.android.com/apk/res/android\"",
    "app=\"http://schemas.android.com/apk/res-auto\"",
    "tools=\"http://schemas.android.com/tools\"",
  };

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet resultSet) {
    PsiElement position = parameters.getPosition();
    PsiElement originalPosition = parameters.getOriginalPosition();
    AndroidFacet facet = AndroidFacet.getInstance(position);

    if (facet == null) {
      return;
    }
    PsiElement parent = position.getParent();
    PsiElement originalParent = originalPosition != null ? originalPosition.getParent() : null;

    if (parent instanceof XmlTag) {
      XmlTag tag = (XmlTag)parent;

      if (tag.getParentTag() != null) {
        return;
      }
      ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());

      if (startTagName == null || startTagName.getPsi() != position) {
        return;
      }
      PsiFile file = tag.getContainingFile();
      if (!(file instanceof XmlFile)) {
        return;
      }
      PsiReference reference = file.findReferenceAt(parameters.getOffset());
      if (reference != null) {
        PsiElement element = reference.getElement();
        int refOffset = element.getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset();
        if (refOffset != position.getTextRange().getStartOffset()) {
          // do not provide completion if we're inside some reference starting in the middle of tag name
          return;
        }
      }

      if (!completeRootTagNames(facet, (XmlFile)file, resultSet)) {
        resultSet.stopHere();
      }
    }
    else if (parent instanceof XmlAttribute) {
      ASTNode attrName = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(parent.getNode());

      if (attrName == null || attrName.getPsi() != position) {
        return;
      }
      XmlAttribute attribute = (XmlAttribute)parent;
      String namespace = attribute.getNamespace();

      XmlTag tag = attribute.getParent();
      DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

      if (!(element instanceof AndroidDomElement)) {
        return;
      }

      // We want to show completion variants for designtime attributes only if "tools:" prefix
      // has already been typed
      if (SdkConstants.TOOLS_URI.equals(namespace)) {
        addDesignTimeAttributes(attribute.getNamespacePrefix(), position, facet, attribute, resultSet);
      }

      addDataBindingAttributes(position, facet, attribute, resultSet, (AndroidDomElement)element);

      if (element instanceof LayoutElement) {
        addAndCustomizeAttributesForLayoutElement(facet, parameters, attribute, resultSet);
      }

      if (tag.getParentTag() == null) {
        if (attribute.getNamespacePrefix().equals(NAMESPACE_PREFIX)) {
          addNamespaces(resultSet, tag.getLocalNamespaceDeclarations(), false);
        }
        else if (namespace.isEmpty()) {
          addNamespaces(resultSet, tag.getLocalNamespaceDeclarations(), true);
        }
      }
    }
    else if (originalParent instanceof XmlAttributeValue) {
      completeTailsInFlagAttribute(parameters, resultSet, (XmlAttributeValue)originalParent);
      completeDataBindingTypeAttr(parameters, resultSet, (XmlAttributeValue)originalParent);
    }
  }

  private void addNamespaces(CompletionResultSet resultSet,
                             @NotNull Map<String, String> namespaces,
                             boolean withPrefix) {
    Collection<String> declaredNamespaces = namespaces.values();
    Stream<String> lookupStrings = Arrays.stream(AVAILABLE_NAMESPACES)
      .filter(availableNamespace -> declaredNamespaces.stream().noneMatch(availableNamespace::contains));
    if (withPrefix) {
      lookupStrings = lookupStrings.map(it -> NAMESPACE_PREFIX + ":" + it);
    }
    resultSet.addAllElements(lookupStrings.map(LookupElementBuilder::create).collect(Collectors.toList()));
  }

  private static void addAll(Collection<String> collection, CompletionResultSet set) {
    for (String s : collection) {
      set.addElement(LookupElementBuilder.create(s));
    }
  }

  // TODO: replace with namespaces. See org.jetbrains.android.dom.AndroidXmlExtension.getNSDescriptor
  private static boolean completeRootTagNames(@NotNull AndroidFacet facet, @NotNull XmlFile xmlFile, @NotNull CompletionResultSet resultSet) {
    if (ManifestDomFileDescription.isManifestFile(xmlFile, facet)) {
      resultSet.addElement(LookupElementBuilder.create("manifest"));
      return false;
    }
    else if (AndroidResourceDomFileDescription.isFileInResourceFolderType(xmlFile, ResourceFolderType.ANIM)) {
      addAll(AndroidAnimationUtils.getPossibleRoots(), resultSet);
      return false;
    }
    else if (AndroidResourceDomFileDescription.isFileInResourceFolderType(xmlFile, ResourceFolderType.ANIMATOR)) {
      addAll(AndroidAnimatorUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (XmlResourceDomFileDescription.isXmlResourceFile(xmlFile)) {
      addAll(AndroidXmlResourcesUtil.ROOT_TAGS, resultSet);
      return false;
    }
    else if (TransitionDomFileDescription.isTransitionFile(xmlFile)) {
      addAll(TransitionDomUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (AndroidColorDomUtil.isColorResourceFile(xmlFile)) {
      addAll(AndroidColorDomUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (RawDomFileDescription.isRawFile(xmlFile)) {
      resultSet.addElement(LookupElementBuilder.create(SdkConstants.TAG_RESOURCES));
      return false;
    }
    else if (AndroidResourceDomFileDescription.isFileInResourceFolderType(xmlFile, ResourceFolderType.MIPMAP)) {
      addAll(AndroidDrawableDomUtil.getPossibleRoots(facet, ResourceFolderType.MIPMAP), resultSet);
      return false;
    }
    else if (FontFamilyDomFileDescription.isFontFamilyFile(xmlFile)) {
      resultSet.addElement(LookupElementBuilder.create(FontFamilyDomFileDescription.TAG_NAME));
      return false;
    }

    return true;
  }

  /**
   * For every regular layout element attribute, add it with "tools:" prefix
   * (or whatever user uses for tools namespace)
   * <p/>
   * <a href="https://developer.android.com/studio/write/tool-attributes.html#design-time_view_attributes">Designtime attributes docs</a>
   */
  private static void addDesignTimeAttributes(@NotNull String namespacePrefix,
                                              @NotNull PsiElement psiElement,
                                              @NotNull AndroidFacet facet,
                                              @NotNull XmlAttribute attribute,
                                              @NotNull CompletionResultSet resultSet) {
    XmlTag tag = attribute.getParent();
    DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

    Set<XmlName> registeredAttributes = new HashSet<>();

    if (element instanceof LayoutElement) {
      AttributeProcessingUtil.processLayoutAttributes(facet, tag, (LayoutElement)element, registeredAttributes,
                                                      (xmlName, attrDef, parentStyleableName) -> {
        if (SdkConstants.ANDROID_URI.equals(xmlName.getNamespaceKey())) {
          String realName = XmlAttributeImpl.getRealName(attribute);
          String lookupElementString =
            realName.length() == 0 ? namespacePrefix + ":" + xmlName.getLocalName() : xmlName.getLocalName();

          LookupElementBuilder lookupElement =
            LookupElementBuilder.create(psiElement, lookupElementString).withInsertHandler(XmlAttributeInsertHandler.INSTANCE);
          resultSet.addElement(lookupElement);
        }
        return null;
      });
    }
  }

  private static void addAndCustomizeAttributesForLayoutElement(AndroidFacet facet,
                                                                CompletionParameters parameters,
                                                                XmlAttribute attribute,
                                                                CompletionResultSet resultSet) {
    XmlTag tag = attribute.getParent();

    boolean localNameCompletion;

    if (attribute.getName().contains(":")) {
      String nsPrefix = attribute.getNamespacePrefix();

      if (nsPrefix.isEmpty()) {
        return;
      }
      if (!SdkConstants.ANDROID_URI.equals(tag.getNamespaceByPrefix(nsPrefix))) {
        return;
      }
      else {
        localNameCompletion = true;
      }
    }
    else {
      localNameCompletion = false;
    }
    Map<String, String> prefix2ns = new HashMap<>();

    resultSet.runRemainingContributors(parameters, result -> {
      LookupElement lookupElement = result.getLookupElement();
      Object obj = lookupElement.getObject();

      if (obj instanceof String) {
        String s = (String)obj;
        int index = s.indexOf(':');

        String attributeName = s.substring(index + 1);
        if (index > 0) {
          String prefix = s.substring(0, index);
          String ns = prefix2ns.get(prefix);

          if (ns == null) {
            ns = tag.getNamespaceByPrefix(prefix);
            prefix2ns.put(prefix, ns);
          }
          if (SdkConstants.ANDROID_URI.equals(ns)) {
            boolean deprecated = isFrameworkAttributeDeprecated(facet, attribute, attributeName);
            result = customizeLayoutAttributeLookupElement(lookupElement, result, attributeName, deprecated);
          }
        }
        else if (localNameCompletion) {
          result = customizeLayoutAttributeLookupElement(lookupElement, result, attributeName, false);
        }
      }
      resultSet.passResult(result);
    });
  }

  private static boolean isFrameworkAttributeDeprecated(AndroidFacet facet, XmlAttribute attribute, String attributeName) {
    ResourceManager manager = ModuleResourceManagers.getInstance(facet).getResourceManager(SYSTEM_RESOURCE_PACKAGE, attribute.getParent());
    if (manager == null) {
      return false;
    }

    AttributeDefinitions attributes = manager.getAttributeDefinitions();
    if (attributes == null) {
      return false;
    }

    AttributeDefinition attributeDefinition = attributes.getAttrDefByName(attributeName);
    return attributeDefinition != null && attributeDefinition.isAttributeDeprecated();
  }

  private static CompletionResult customizeLayoutAttributeLookupElement(LookupElement lookupElement,
                                                                        CompletionResult result,
                                                                        String localName,
                                                                        boolean markDeprecated) {
    if (!localName.startsWith(LAYOUT_ATTRIBUTE_PREFIX)) {
      if (markDeprecated) {
        return result.withLookupElement(PrioritizedLookupElement.withPriority(new LookupElementDecorator<LookupElement>(lookupElement) {
          @Override
          public void renderElement(LookupElementPresentation presentation) {
            super.renderElement(presentation);
            presentation.setStrikeout(true);
          }
        }, -1.0));
      }
      return result;
    }
    String localSuffix = localName.substring(LAYOUT_ATTRIBUTE_PREFIX.length());

    if (!localSuffix.isEmpty()) {
      HashSet<String> lookupStrings = new HashSet<>(lookupElement.getAllLookupStrings());
      lookupStrings.add(localSuffix);

      lookupElement = new LookupElementDecorator<LookupElement>(lookupElement) {
        @Override
        public Set<String> getAllLookupStrings() {
          return lookupStrings;
        }

        @Override
        public void renderElement(LookupElementPresentation presentation) {
          super.renderElement(presentation);
          presentation.setStrikeout(markDeprecated);
        }
      };
    }
    return result.withLookupElement(PrioritizedLookupElement.withPriority(lookupElement, 100.0));
  }

  /**
   * Adds the XML attributes that come from {@code @BindingAdapter} annotations
   */
  private static void addDataBindingAttributes(@NotNull PsiElement position,
                                               @NotNull AndroidFacet facet,
                                               @NotNull XmlAttribute attribute,
                                               @NotNull CompletionResultSet resultSet,
                                               AndroidDomElement element) {
    PsiFile containingFile = attribute.getContainingFile();
    if (!(containingFile instanceof XmlFile) || !DataBindingDomFileDescription.hasDataBindingRootTag((XmlFile)containingFile)) {
      // Not a databinding XML layout
      return;
    }

    DataBindingAnnotationsService bindingAnnotationsService = DataBindingAnnotationsService.getInstance(facet);
    /*
     * Avoid offering completion for already existing in attr.xml attributes. We only want to add those attributes that are only added via
     * @BindingAdapter.
     */
    LinkedHashSet<String> alreadyDeclared = new LinkedHashSet<>();
    AttributeProcessingUtil.processAttributes(element, facet, true, (xmlName, attrDef, parentStyleableName) -> {
      alreadyDeclared.add(xmlName.getLocalName());
      return null;
    });

    bindingAnnotationsService.getBindingAdapterAttributes().forEach((dataBindingAttribute) -> {
      if (!alreadyDeclared.contains(dataBindingAttribute)) {
        resultSet.addElement(LookupElementBuilder.create(position, dataBindingAttribute)
                               .withInsertHandler(XmlAttributeInsertHandler.INSTANCE));
      }
    });
  }

  private static void completeDataBindingTypeAttr(CompletionParameters parameters,
                                                  CompletionResultSet resultSet,
                                                  XmlAttributeValue originalParent) {
    PsiElement gp = originalParent.getParent();
    if (!(gp instanceof XmlAttribute)) {
      return;
    }
    GenericAttributeValue<?> domElement = DomManager.getDomManager(gp.getProject()).getDomElement((XmlAttribute)gp);
    if (domElement == null) {
      return;
    }
    if ((DomUtil.getParentOfType(domElement, Data.class, true) != null && ((XmlAttribute)gp).getName().equals(SdkConstants.ATTR_TYPE))) {
      // Ensure that the parent tag of the tag containing the attribute is "<data>" and the attribute being edited is "type"
      DataBindingCompletionUtil.addCompletions(parameters, resultSet);
    }
  }

  private static void completeTailsInFlagAttribute(CompletionParameters parameters,
                                                   CompletionResultSet resultSet,
                                                   XmlAttributeValue parent) {
    String currentValue = parent.getValue();

    if (currentValue.isEmpty() || currentValue.endsWith("|")) {
      return;
    }
    PsiElement grandparent = parent.getParent();

    if (!(grandparent instanceof XmlAttribute)) {
      return;
    }
    GenericAttributeValue domValue = DomManager.getDomManager(grandparent.getProject()).getDomElement((XmlAttribute)grandparent);
    Converter<?> converter = domValue != null ? domValue.getConverter() : null;

    if (!(converter instanceof FlagConverter)) {
      return;
    }
    TextRange valueRange = parent.getValueTextRange();

    if (valueRange != null && valueRange.getEndOffset() == parameters.getOffset()) {
      Set<String> valueSet = ((FlagConverter)converter).getValues();

      if (!valueSet.isEmpty()) {
        String prefix = resultSet.getPrefixMatcher().getPrefix();

        if (valueSet.contains(prefix)) {
          ArrayList<String> filteredValues = new ArrayList<>(valueSet);
          //noinspection unchecked
          DelimitedListConverter.filterVariants(filteredValues, domValue);

          for (String variant : filteredValues) {
            resultSet.addElement(LookupElementBuilder.create(prefix + "|" + variant));
          }
        }
      }
    }
  }
}
