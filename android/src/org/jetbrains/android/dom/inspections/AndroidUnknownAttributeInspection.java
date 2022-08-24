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
package org.jetbrains.android.dom.inspections;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.dom.AndroidAnyAttributeDescriptor;
import org.jetbrains.android.dom.AndroidAnyTagDescriptor;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.TagFromClassDescriptor;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AndroidUnknownAttributeInspection extends LocalInspectionTool {
  private static volatile Set<ResourceFolderType> ourSupportedResourceTypes;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.unknown.attribute.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "AndroidUnknownAttribute";
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }

    if (isMyFile(facet, (XmlFile)file)) {
      MyVisitor visitor = new MyVisitor(manager, isOnTheFly);
      file.accept(visitor);
      return visitor.myResult.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  static boolean isMyFile(@NotNull AndroidFacet facet, XmlFile file) {
    ResourceFolderType resourceType = ModuleResourceManagers.getInstance(facet).getLocalResourceManager().getFileResourceFolderType(file);
    if (resourceType != null) {
      if (ourSupportedResourceTypes == null) {
        ourSupportedResourceTypes = EnumSet.complementOf(EnumSet.of(ResourceFolderType.INTERPOLATOR, ResourceFolderType.VALUES));
      }
      // Raw resource files should accept any tag values
      if (!ourSupportedResourceTypes.contains(resourceType) || ResourceFolderType.RAW == resourceType) {
        return false;
      }
      if (ResourceFolderType.XML == resourceType) {
        final XmlTag rootTag = file.getRootTag();
        return rootTag != null && AndroidXmlResourcesUtil.isSupportedRootTag(facet, rootTag.getName());
      }
      return true;
    }
    return ManifestDomFileDescription.isManifestFile(file, facet);
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    final List<ProblemDescriptor> myResult = new ArrayList<>();

    private MyVisitor(InspectionManager inspectionManager, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
      if (!"xmlns".equals(attribute.getNamespacePrefix())) {
        String namespace = attribute.getNamespace();

        if (SdkConstants.ANDROID_URI.equals(namespace) || namespace.isEmpty()) {
          final XmlTag tag = attribute.getParent();

          if (tag == null) return;

          XmlElementDescriptor descriptor = tag.getDescriptor();
          if (descriptor instanceof AndroidAnyTagDescriptor ||
              descriptor instanceof TagFromClassDescriptor && ((TagFromClassDescriptor)descriptor).getClazz() == null) {
            // Don't register warning for unknown tags tags or for unresolved Views.
            return;
          }

          if (attribute.getDescriptor() instanceof AndroidAnyAttributeDescriptor) {

            final ASTNode node = attribute.getNode();
            assert node != null;
            ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
            final PsiElement nameElement = nameNode != null ? nameNode.getPsi() : null;
            if (nameElement != null) {
              // If here, it means we might have an attribute that is dynamically provided by data binding. We can use
              // ReferenceProvidersRegistry which ultimately delegates to DataBindingXmlAttributeReferenceContributor, which will return
              // references if it found any, e.g. those annotated with @BindingAdapter and @BindingMethod.
              PsiReference[] providers = ReferenceProvidersRegistry.getReferencesFromProviders(attribute);
              if (providers.length == 0) {
                myResult.add(myInspectionManager.createProblemDescriptor(
                  nameElement, AndroidBundle.message("android.inspections.unknown.attribute.message", attribute.getName()), myOnTheFly,
                  LocalQuickFix.EMPTY_ARRAY,
                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              }
            }
          }
        }
      }
    }
  }
}
