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
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.*;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.converters.DelimitedListConverter;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animation.AnimationDomFileDescription;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.animator.AnimatorDomFileDescription;
import org.jetbrains.android.dom.color.ColorDomFileDescription;
import org.jetbrains.android.dom.converters.FlagConverter;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.drawable.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author coyote
 */
public class AndroidCompletionContributor extends CompletionContributor {

  private static void addAll(Collection<String> collection, CompletionResultSet set) {
    for (String s : collection) {
      set.addElement(LookupElementBuilder.create(s));
    }
  }

  private static boolean completeTagNames(@NotNull AndroidFacet facet, @NotNull XmlFile xmlFile, @NotNull CompletionResultSet resultSet) {
    if (ManifestDomFileDescription.isManifestFile(xmlFile)) {
      resultSet.addElement(LookupElementBuilder.create("manifest"));
      return false;
    }
    else if (LayoutDomFileDescription.isLayoutFile(xmlFile)) {
      addAll(AndroidLayoutUtil.getPossibleRoots(facet), resultSet);
      return false;
    }
    else if (AnimationDomFileDescription.isAnimationFile(xmlFile)) {
      addAll(AndroidAnimationUtils.getPossibleChildren(facet), resultSet);
      return false;
    }
    else if (AnimatorDomFileDescription.isAnimatorFile(xmlFile)) {
      addAll(AndroidAnimatorUtil.getPossibleChildren(), resultSet);
      return false;
    }
    else if (XmlResourceDomFileDescription.isXmlResourceFile(xmlFile)) {
      addAll(AndroidXmlResourcesUtil.getPossibleRoots(facet), resultSet);
      return false;
    }
    else if (AndroidDrawableDomUtil.isDrawableResourceFile(xmlFile)) {
      addAll(AndroidDrawableDomUtil.getPossibleRoots(), resultSet);
      return false;
    }
    else if (ColorDomFileDescription.isColorResourceFile(xmlFile)) {
      addAll(Arrays.asList(DrawableStateListDomFileDescription.SELECTOR_TAG_NAME), resultSet);
      return false;
    }
    return true;
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet resultSet) {
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
          attrName.getPsi() != position ||
          position.getText().startsWith("android:")) {
        return;
      }

      final PsiElement gp = parent.getParent();
      if (!(gp instanceof XmlTag)) {
        return;
      }

      final DomElement element = DomManager.getDomManager(gp.getProject()).getDomElement((XmlTag)gp);
      if (!(element instanceof LayoutElement) &&
          !(element instanceof PreferenceElement)) {
        return;
      }

      final String prefix = ((XmlTag)gp).getPrefixByNamespace(SdkConstants.NS_RESOURCES);
      if (prefix == null || prefix.length() < 3) {
        return;
      }
      final LookupElementBuilder e = LookupElementBuilder.create(prefix + ":").withTypeText("[Namespace Prefix]", true);
      resultSet.addElement(PrioritizedLookupElement.withPriority(e, Double.MAX_VALUE));
    }
    else if (originalParent instanceof XmlAttributeValue) {
      completeTailsInFlagAttribute(parameters, resultSet, (XmlAttributeValue)originalParent);
    }
  }

  private static void completeTailsInFlagAttribute(CompletionParameters parameters,
                                                   CompletionResultSet resultSet,
                                                   XmlAttributeValue parent) {
    final String currentValue = parent.getValue();

    if (currentValue == null || currentValue.length() == 0 || currentValue.endsWith("|")) {
      return;
    }
    final PsiElement gp = parent.getParent();

    if (!(gp instanceof XmlAttribute)) {
      return;
    }
    final GenericAttributeValue domValue = DomManager.getDomManager(gp.getProject()).getDomElement((XmlAttribute)gp);
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
