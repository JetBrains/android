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

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.lang.databinding.DataBindingCompletionUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.DelimitedListConverter;
import com.intellij.util.xml.reflect.DomExtension;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.color.ColorDomFileDescription;
import org.jetbrains.android.dom.converters.FlagConverter;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.drawable.fileDescriptions.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.dom.layout.Data;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.raw.RawDomFileDescription;
import org.jetbrains.android.dom.transition.TransitionDomFileDescription;
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;


public class AndroidCompletionContributor extends CompletionContributor {

  private static final String LAYOUT_ATTRIBUTE_PREFIX = "layout_";

  private static void addAll(Collection<String> collection, CompletionResultSet set) {
    for (String s : collection) {
      set.addElement(LookupElementBuilder.create(s));
    }
  }

  private static boolean completeTagNames(@NotNull AndroidFacet facet, @NotNull XmlFile xmlFile, @NotNull CompletionResultSet resultSet) {
    if (ManifestDomFileDescription.isManifestFile(xmlFile, facet)) {
      resultSet.addElement(LookupElementBuilder.create("manifest"));
      return false;
    }
    else if (LayoutDomFileDescription.isLayoutFile(xmlFile)) {
      final Map<String,PsiClass> classMap = facet.getClassMap(
        AndroidUtils.VIEW_CLASS_NAME);

      for (String rootTag : AndroidLayoutUtil.getPossibleRoots(facet)) {
        final PsiClass aClass = classMap.get(rootTag);
        LookupElementBuilder builder;
        if (aClass != null) {
          builder = LookupElementBuilder.create(aClass, rootTag);
          final String qualifiedName = aClass.getQualifiedName();
          final String name = qualifiedName == null ? null : AndroidUtils.getUnqualifiedName(qualifiedName);
          if (name != null) {
            builder = builder.withLookupString(name);
          }
        }
        else {
          builder = LookupElementBuilder.create(rootTag);
        }
        final Icon icon = AndroidDomElementDescriptorProvider.getIconForViewTag(rootTag);

        if (icon != null) {
          builder = builder.withIcon(icon);
        }
        resultSet.addElement(builder);
      }
      return false;
    }
    else if (AndroidResourceDomFileDescription.doIsMyFile(xmlFile, ResourceFolderType.ANIM)) {
      addAll(AndroidAnimationUtils.getPossibleRoots(), resultSet);
      return false;
    }
    else if (AndroidResourceDomFileDescription.doIsMyFile(xmlFile, ResourceFolderType.ANIMATOR)) {
      addAll(AndroidAnimatorUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (XmlResourceDomFileDescription.isXmlResourceFile(xmlFile)) {
      addAll(AndroidXmlResourcesUtil.getPossibleRoots(facet), resultSet);
      return false;
    }
    else if (AndroidDrawableDomUtil.isDrawableResourceFile(xmlFile)) {
      addAll(AndroidDrawableDomUtil.getPossibleRoots(facet), resultSet);
      return false;
    }
    else if (TransitionDomFileDescription.isTransitionFile(xmlFile)) {
      addAll(TransitionDomUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (ColorDomFileDescription.isColorResourceFile(xmlFile)) {
      resultSet.addElement(LookupElementBuilder.create(DrawableStateListDomFileDescription.TAG_NAME));
      return false;
    }
    else if (RawDomFileDescription.isRawFile(xmlFile)) {
      resultSet.addElement(LookupElementBuilder.create(SdkConstants.TAG_RESOURCES));
      return false;
    }
    return true;
  }

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
      final ASTNode startTagName = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());

      if (startTagName == null || startTagName.getPsi() != position) {
        return;
      }
      final PsiFile file = tag.getContainingFile();
      if (!(file instanceof XmlFile)) {
        return;
      }
      final PsiReference reference = file.findReferenceAt(parameters.getOffset());
      if (reference != null) {
        final PsiElement element = reference.getElement();
        if (element != null) {
          final int refOffset = element.getTextRange().getStartOffset() +
                                reference.getRangeInElement().getStartOffset();
          if (refOffset != position.getTextRange().getStartOffset()) {
            // do not provide completion if we're inside some reference starting in the middle of tag name
            return;
          }
        }
      }

      if (!completeTagNames(facet, (XmlFile)file, resultSet)) {
        resultSet.stopHere();
      }
    }
    else if (parent instanceof XmlAttribute) {
      final ASTNode attrName = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(parent.getNode());

      if (attrName == null ||
          attrName.getPsi() != position) {
        return;
      }
      addAndroidPrefixElement(position, parent, resultSet);
      final XmlAttribute attribute = (XmlAttribute)parent;
      final String namespace = attribute.getNamespace();

      // We want to show completion variants for designtime attributes only if "tools:" prefix
      // has already been typed
      if (SdkConstants.TOOLS_URI.equals(namespace)) {
        addDesignTimeAttributes(attribute.getNamespacePrefix(), position, facet, attribute, resultSet);
      }
      customizeAddedAttributes(facet, parameters, attribute, resultSet);
    }
    else if (originalParent instanceof XmlAttributeValue) {
      completeTailsInFlagAttribute(parameters, resultSet, (XmlAttributeValue)originalParent);
      completeDataBindingTypeAttr(parameters, resultSet, (XmlAttributeValue)originalParent);
    }
  }

  /**
   * For every regular layout element attribute, add it with "tools:" prefix
   * (or whatever user uses for tools namespace)
   * <p/>
   * <a href="http://tools.android.com/tips/layout-designtime-attributes">Designtime attributes docs</a>
   */
  private static void addDesignTimeAttributes(@NotNull final String namespacePrefix,
                                              @NotNull final PsiElement psiElement,
                                              @NotNull final AndroidFacet facet,
                                              @NotNull final XmlAttribute attribute,
                                              @NotNull final CompletionResultSet resultSet) {
    final XmlTag tag = attribute.getParent();
    final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);

    // Quirk of AndroidDomExtender "API": we need to create mutable sets to pass them to registerExtensionsForLayout
    final Set<String> registeredSubtags = new HashSet<String>();
    final Set<XmlName> registeredAttributes = new HashSet<XmlName>();

    if (element instanceof LayoutElement) {
      AndroidDomExtender.registerExtensionsForLayout(facet, tag, (LayoutElement)element, new AndroidDomExtender.MyCallback() {
        @Nullable
        @Override
        public DomExtension processAttribute(@NotNull XmlName xmlName,
                                             @NotNull AttributeDefinition attrDef,
                                             @Nullable String parentStyleableName) {
          if (SdkConstants.ANDROID_URI.equals(xmlName.getNamespaceKey())) {
            final String localName = xmlName.getLocalName();

            // Lookup string is something that would be inserted when attribute is completed, so we want to use
            // local name as an argument of .create(), otherwise we'll end up with getting completions like
            // "tools:tools:src". However, we want to show "tools:" prefix in the completion list, and for that
            // .withPresentableText is used
            final LookupElementBuilder element =
              LookupElementBuilder.create(psiElement, localName).withInsertHandler(XmlAttributeInsertHandler.INSTANCE)
                .withPresentableText(namespacePrefix + ":" + localName);
            resultSet.addElement(element);
          }
          return null;
        }
      }, registeredSubtags, registeredAttributes);
    }
  }

  private static void addAndroidPrefixElement(PsiElement position, PsiElement parent, CompletionResultSet resultSet) {
    if (position.getText().startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)) {
      return;
    }

    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof XmlTag)) {
      return;
    }

    final DomElement element = DomManager.getDomManager(grandparent.getProject()).getDomElement((XmlTag)grandparent);
    if (!(element instanceof LayoutElement) &&
        !(element instanceof PreferenceElement)) {
      return;
    }

    final String prefix = ((XmlTag)grandparent).getPrefixByNamespace(SdkConstants.NS_RESOURCES);
    if (prefix == null || prefix.length() < 3) {
      return;
    }
    final LookupElementBuilder e = LookupElementBuilder.create(prefix + ":").withTypeText("[Namespace Prefix]", true);
    resultSet.addElement(PrioritizedLookupElement.withPriority(e, Double.MAX_VALUE));
  }

  private static void customizeAddedAttributes(final AndroidFacet facet,
                                               CompletionParameters parameters,
                                               final XmlAttribute attribute,
                                               final CompletionResultSet resultSet) {
    final PsiElement gp = attribute.getParent();

    if (gp == null) {
      return;
    }
    final XmlTag tag = (XmlTag)gp;
    final DomElement element = DomManager.getDomManager(gp.getProject()).getDomElement(tag);

    if (!(element instanceof LayoutElement)) {
      return;
    }
    final boolean localNameCompletion;

    if (attribute.getName().contains(":")) {
      final String nsPrefix = attribute.getNamespacePrefix();

      if (nsPrefix.length() == 0) {
        return;
      }
      if (!SdkConstants.NS_RESOURCES.equals(tag.getNamespaceByPrefix(nsPrefix))) {
        return;
      }
      else {
        localNameCompletion = true;
      }
    }
    else {
      localNameCompletion = false;
    }
    final Map<String, String> prefix2ns = new HashMap<String, String>();

    resultSet.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
      @Override
      public void consume(CompletionResult result) {
        LookupElement lookupElement = result.getLookupElement();
        final Object obj = lookupElement.getObject();

        if (obj instanceof String) {
          final String s = (String)obj;
          final int index = s.indexOf(':');

          final String attributeName = s.substring(index + 1);
          if (index > 0) {
            final String prefix = s.substring(0, index);
            String ns = prefix2ns.get(prefix);

            if (ns == null) {
              ns = tag.getNamespaceByPrefix(prefix);
              prefix2ns.put(prefix, ns);
            }
            if (SdkConstants.NS_RESOURCES.equals(ns)) {
              final boolean deprecated = isFrameworkAttributeDeprecated(facet, attribute, attributeName);
              result = customizeLayoutAttributeLookupElement(lookupElement, result, attributeName, deprecated);
            }
          }
          else if (localNameCompletion) {
            result = customizeLayoutAttributeLookupElement(lookupElement, result, attributeName, false);
          }
        }
        resultSet.passResult(result);
      }
    });
  }

  private static boolean isFrameworkAttributeDeprecated(AndroidFacet facet, XmlAttribute attribute, String attributeName) {
    final ResourceManager manager = facet.getResourceManager(AndroidUtils.SYSTEM_RESOURCE_PACKAGE, attribute.getParent());
    if (manager == null) {
      return false;
    }

    final AttributeDefinitions attributes = manager.getAttributeDefinitions();
    if (attributes == null) {
      return false;
    }

    final AttributeDefinition attributeDefinition = attributes.getAttrDefByName(attributeName);
    return attributeDefinition != null && attributeDefinition.isAttributeDeprecated();
  }

  private static CompletionResult customizeLayoutAttributeLookupElement(LookupElement lookupElement,
                                                                        CompletionResult result,
                                                                        String localName,
                                                                        final boolean markDeprecated) {
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
    final String localSuffix = localName.substring(LAYOUT_ATTRIBUTE_PREFIX.length());

    if (localSuffix.length() > 0) {
      final HashSet<String> lookupStrings = new HashSet<String>(lookupElement.getAllLookupStrings());
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

  private static void completeDataBindingTypeAttr(CompletionParameters parameters,
                                                  CompletionResultSet resultSet,
                                                  XmlAttributeValue originalParent) {
    PsiElement gp = originalParent.getParent();
    if (!(gp instanceof XmlAttribute)) {
      return;
    }
    GenericAttributeValue domElement = DomManager.getDomManager(gp.getProject()).getDomElement((XmlAttribute)gp);
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
    final String currentValue = parent.getValue();

    if (currentValue == null || currentValue.length() == 0 || currentValue.endsWith("|")) {
      return;
    }
    final PsiElement grandparent = parent.getParent();

    if (!(grandparent instanceof XmlAttribute)) {
      return;
    }
    final GenericAttributeValue domValue = DomManager.getDomManager(grandparent.getProject()).getDomElement((XmlAttribute)grandparent);
    final Converter converter = domValue != null ? domValue.getConverter() : null;

    if (!(converter instanceof FlagConverter)) {
      return;
    }
    final TextRange valueRange = parent.getValueTextRange();

    if (valueRange != null && valueRange.getEndOffset() == parameters.getOffset()) {
      final Set<String> valueSet = ((FlagConverter)converter).getValues();

      if (valueSet.size() > 0) {
        final String prefix = resultSet.getPrefixMatcher().getPrefix();

        if (valueSet.contains(prefix)) {
          final ArrayList<String> filteredValues = new ArrayList<String>(valueSet);
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
