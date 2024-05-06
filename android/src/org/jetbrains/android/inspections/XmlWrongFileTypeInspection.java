/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.utils.text.TextUtilsKt;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.animation.AndroidAnimationUtils;
import org.jetbrains.android.dom.animator.AndroidAnimatorUtil;
import org.jetbrains.android.dom.color.AndroidColorDomUtil;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.transition.TransitionDomUtil;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inspection that provides a quickfix to move file to resource folder which is recommended
 * by documentation. A use case: if a developer puts &lt;objectAnimator&gt; XML file in
 * anim/ folder instead of animator/, AS would fail to parse a file, providing a cryptic
 * error message. This inspection would provide a better error message and a quick fix
 * that would automatically move file to required location.
 */
public class XmlWrongFileTypeInspection extends LocalInspectionTool {
  private static ImmutableMultimap<String, ResourceFolderType> ourResourceFolderTypeMap;

  /**
   * Function for extracting quoted folder name from {@link ResourceFolderType}.
   */
  private static final Function<ResourceFolderType, String> TYPE_NAME_FUNCTION = new Function<ResourceFolderType, String>() {
    @NotNull
    @Override
    public String apply(ResourceFolderType input) {
      return '"' + input.getName() + '"';
    }
  };

  @NotNull
  public static ImmutableCollection<ResourceFolderType> determineResourceFolderTypeByRootTag(@NotNull AndroidFacet facet,
                                                                                             @NotNull String tagName) {
    if (ourResourceFolderTypeMap == null) {
      // First time calling the function, need to initialize the map first
      final ImmutableMultimap.Builder<String, ResourceFolderType> builder = ImmutableMultimap.builder();
      for (String tag : AndroidAnimationUtils.getPossibleRoots()) {
        builder.put(tag, ResourceFolderType.ANIM);
      }
      for (String tag : AndroidAnimatorUtil.getPossibleRoots()) {
        builder.put(tag, ResourceFolderType.ANIMATOR);
      }
      for (String tag : AndroidXmlResourcesUtil.getPossibleRoots(facet)) {
        builder.put(tag, ResourceFolderType.XML);
      }
      for (String tag : AndroidDrawableDomUtil.getPossibleRoots(facet)) {
        builder.put(tag, ResourceFolderType.DRAWABLE);
      }
      for (String tag : TransitionDomUtil.getPossibleRoots()) {
        builder.put(tag, ResourceFolderType.TRANSITION);
      }
      for (String tag : AndroidColorDomUtil.getPossibleRoots()) {
        builder.put(tag, ResourceFolderType.COLOR);
      }
      ourResourceFolderTypeMap = builder.build();
    }
    return ourResourceFolderTypeMap.get(tagName);
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return null;
    }

    final XmlFile xmlFile = (XmlFile)file;
    final XmlTag rootTag = xmlFile.getRootTag();
    if (rootTag == null) {
      return null;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }

    final DomFileDescription<?> description = DomManager.getDomManager(file.getProject()).getDomFileDescription(xmlFile);
    // File is recognized correctly, there are no need to show an error
    if (description != null) {
      return null;
    }

    final PsiDirectory directory = file.getContainingDirectory();
    if (directory == null) {
      return null;
    }

    if (!IdeResourcesUtil.isResourceSubdirectory(directory, null, true)) {
      return null;
    }

    final String name = rootTag.getName();
    final ImmutableCollection<ResourceFolderType> folderTypes = determineResourceFolderTypeByRootTag(facet, name);
    if (folderTypes.isEmpty()) {
      return null;
    }

    final String directoryName = directory.getName();

    final String resourceType;
    final String resourceQualifier;

    // If current resource directory has a qualifier we want to preserve it while moving
    final int dashIndex = directoryName.indexOf('-');
    if (dashIndex != -1) {
      resourceType = directoryName.substring(0, dashIndex);
      resourceQualifier = directoryName.substring(dashIndex + 1);
    }
    else {
      resourceType = directoryName;
      resourceQualifier = null;
    }

    String folderEnumeration =
      TextUtilsKt.toCommaSeparatedList(Collections2.transform(folderTypes, TYPE_NAME_FUNCTION), "or");

    if (folderTypes.size() > 1) {
      folderEnumeration = "either " + folderEnumeration;
    }

    final String message = String.format("<%1$s> XML file should be in %2$s, not \"%3$s\"", name, folderEnumeration, resourceType);
    final ASTNode node = XmlChildRole.START_TAG_NAME_FINDER.findChild(rootTag.getNode());

    final LocalQuickFix[] quickFixes = new LocalQuickFix[folderTypes.size()];
    int i = 0;
    for (ResourceFolderType type : folderTypes) {
      final String resultFolder = resourceQualifier == null ? type.getName() : type.getName() + '-' + resourceQualifier;
      quickFixes[i++] = new MoveFileQuickFix(resultFolder, xmlFile);
    }

    final ProblemDescriptor descriptor =
      manager.createProblemDescriptor(node == null ? rootTag : node.getPsi(), message, isOnTheFly,
                                      quickFixes, ProblemHighlightType.GENERIC_ERROR);

    return new ProblemDescriptor[]{descriptor};
  }

}
