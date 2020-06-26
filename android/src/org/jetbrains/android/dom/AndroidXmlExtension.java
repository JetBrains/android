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
package org.jetbrains.android.dom;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.client.api.LintXmlConfiguration;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.DefaultXmlExtension;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.android.dom.layout.AndroidLayoutNSDescriptor;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.xml.XmlResourceNSDescriptor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.TagFromClassDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlExtension extends DefaultXmlExtension {
  private static final SchemaPrefix EMPTY_SCHEMA = new SchemaPrefix(null, new TextRange(0, 0), SdkConstants.ANDROID_NS_NAME);

  @Nullable
  @Override
  public XmlNSDescriptor getNSDescriptor(XmlTag element, String namespace, boolean strict) {
    XmlFile file = (XmlFile)element.getContainingFile();
    boolean isRoot = file.getRootTag() == element;
    if (isRoot && isFileInResourceFolderType(file, ResourceFolderType.LAYOUT)) {
      return AndroidLayoutNSDescriptor.INSTANCE;
    }
    if (isRoot && isFileInResourceFolderType(file, ResourceFolderType.XML)) {
      return XmlResourceNSDescriptor.INSTANCE;
    }
    return super.getNSDescriptor(element, namespace, strict);
  }

  @Nullable
  @Override
  public TagNameReference createTagNameReference(ASTNode nameElement, boolean startTagFlag) {
    return new TagNameReference(nameElement, startTagFlag) {
      @Override
      public boolean isSoft() {
        // To avoid default errors for unresolved tags.
        return true;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (element instanceof PsiQualifiedNamedElement) {
          // This case is handled by AndroidXmlReferenceProvider.
          return null;
        }
        return super.bindToElement(element);
      }

      @Override
      public @Nullable PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        final XmlTag element = getTagElement();
        if (element != null && element.getDescriptor() instanceof TagFromClassDescriptor) {
          // This case is handled by AndroidXmlReferenceProvider.
          return null;
        }
        return super.handleElementRename(newElementName);
      }
    };
  }

  @Override
  public boolean isAvailable(final PsiFile file) {
    if (file instanceof XmlFile) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          if (AndroidFacet.getInstance(file) != null) {
            if (IdeResourcesUtil.isInResourceSubdirectory(file, null)) {
              return true;
            }

            if (file.getName().equals(FN_ANDROID_MANIFEST_XML) && ManifestDomFileDescription.isManifestFile((XmlFile)file)) {
              return true;
            }
          }

          if (LintXmlConfiguration.CONFIG_FILE_NAME.equals(file.getName())) {
            return true;
          }

          XmlFile xmlFile = (XmlFile)file;
          XmlTag tag = xmlFile.getRootTag();
          if (tag != null) {
            String tagName = tag.getName();
            if (LintXmlConfiguration.TAG_LINT.equals(tagName) || SdkConstants.TAG_ISSUES.equals(tagName)) {
              return true;
            }
          }

          return false;
        }
      });
    }
    return false;
  }

  @Override
  public SchemaPrefix getPrefixDeclaration(final XmlTag context, String namespacePrefix) {
    SchemaPrefix prefix = super.getPrefixDeclaration(context, namespacePrefix);
    if (prefix != null) {
      return prefix;
    }

    if (namespacePrefix.isEmpty()) {
      // In for example XHTML documents, the root element looks like this:
      //  <html xmlns="http://www.w3.org/1999/xhtml">
      // This means that the IDE can find the namespace for "".
      //
      // However, in Android XML files it's implicit, so just return a dummy SchemaPrefix so
      // // that we don't end up with a
      //      Namespace ''{0}'' is not bound
      // error from {@link XmlUnboundNsPrefixInspection#checkUnboundNamespacePrefix}
      return EMPTY_SCHEMA;
    }

    return null;
  }

  @Override
  public boolean isRequiredAttributeImplicitlyPresent(@NotNull XmlTag tag, @NotNull String attrName) {
    return isAaptAttributeDefined(tag, attrName);
  }

  /**
   * Checks if an aapt:attr with the given name is defined for the XML tag.
   *
   * @param tag the XML tag to check
   * @param attrName the name of the attribute to look for
   * @return true if the attribute is defined, false otherwise
   */
  public static boolean isAaptAttributeDefined(@NotNull XmlTag tag, @NotNull String attrName) {
    XmlTag[] subTags = tag.getSubTags();
    for (XmlTag child : subTags) {
      if (SdkConstants.TAG_ATTR.equals(child.getLocalName()) && SdkConstants.AAPT_URI.equals(child.getNamespace())) {
        XmlAttribute attr = child.getAttribute(SdkConstants.ATTR_NAME);
        if (attr != null && attrName.equals(attr.getValue())) {
          return true;
        }
      }
    }
    return false;
  }
}
