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

import static com.android.SdkConstants.ATTR_NAME;

import com.android.annotations.concurrency.WorkerThread;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.google.common.collect.ObjectArrays;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.List;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ValueResourceInfo;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a custom {@link FindUsagesHandler} that understands the connection between XML and R class fields.
 *
 * <p>To find all usages of an Android resource, we need to know what {@link PsiElement}s may conceptually refer to a resource, what {@link
 * PsiReference}s are attached to them and what those references resolve to. Currently the resolve targets may be instances of {@link
 * PsiField} (for light R classes fields), {@link ValueResourceElementWrapper}, {@link FileResourceElementWrapper} or {@link PsiFile}. Note
 * that {@link FileResourceElementWrapper} implements {@link PsiFile} and is equivalent to the {@link PsiFile} it wraps, so needs no
 * special handling.
 */
public class AndroidFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  /**
   * Quickly checks if the given element represents an Android resource and should be treated specially.
   *
   * <p>This method is called often by {@link IdentifierHighlighterPass}, so has to avoid expensive computations. These including touching
   * the DOM layer or finding light R class fields. {@link #createFindUsagesHandler(PsiElement, boolean)} can recover from false positives
   * by returning null, once it checks if we're being invoked for highlighting or refactoring (in which case we can perform long-running
   * computations).
   */
  @WorkerThread
  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (element instanceof ResourceReferencePsiElement) {
      return true;
    }
    if (element instanceof LazyValueResourceElementWrapper) {
      return true;
    }
    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element;
      if (AndroidResourceUtil.isIdDeclaration(value)) {
        return true;
      }
    }
    element = correctResourceElement(element);
    if (element instanceof PsiField) {
      return AndroidResourceUtil.isResourceField((PsiField)element);
    }
    else if (element instanceof PsiFile || element instanceof XmlTag) {
      final AndroidFacet facet = AndroidFacet.getInstance(element);

      if (facet != null) {
        LocalResourceManager resourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
        if (element instanceof PsiFile) {
          return resourceManager.getFileResourceFolderType((PsiFile)element) != null;
        }
        else {
          ResourceFolderType fileResType = resourceManager.getFileResourceFolderType(element.getContainingFile());
          if (ResourceFolderType.VALUES == fileResType) {
            return AndroidResourceUtil.getResourceTypeForResourceTag((XmlTag)element) != null;
          }
        }
      }
    }
    return false;
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    private final PsiElement[] myAdditionalElements;

    protected MyFindUsagesHandler(@NotNull PsiElement element, PsiElement... additionalElements) {
      super(element);
      myAdditionalElements = additionalElements;
    }

    @NotNull
    @Override
    public PsiElement[] getSecondaryElements() {
      return myAdditionalElements;
    }
  }

  @Nullable
  private static PsiElement correctResourceElement(PsiElement element) {
    if (element instanceof XmlElement && !(element instanceof XmlFile)) {
      XmlTag tag = element instanceof XmlTag ? (XmlTag)element : PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag != null && ResourcesDomFileDescription.isResourcesFile((XmlFile)tag.getContainingFile())) {
        return tag;
      }
      return null;
    }
    return element;
  }

  private static XmlAttributeValue wrapIfNecessary(XmlAttributeValue value) {
    if (value instanceof ResourceElementWrapper) {
      return value;
    }
    return new ValueResourceElementWrapper(value);
  }

  /**
   * Provides a custom {@link FindUsagesHandler} for elements related to Android resources. See the class documentation for details. In
   * some cases it returns null, which means that the standard usage handler will be used, i.e. only references to {@code element} will
   * be found.
   *
   * <p>If {@code forHighlightUsages} is true, this method needs to avoid expensive operations, including finding light R class fields. The
   * {@link IdentifierHighlighterPass} runs after every change in an XML file and changing an XML file may cause {@link
   * ResourceFolderRepository} to increase its modification stamp, which means light R fields need to be recomputed, which means merging
   * resources up the {@link ResourceRepository} hierarchy, for the current module as well as all modules that depend on it. Although this
   * methods runs on a background thread, finding R fields causes a flurry of activity, may result in holding the read lock for noticeable
   * amounts of time and increases memory pressure.
   *
   * <p>Finding related light R fields can safely be skipped for highlighting usages, because the usage search is executed only on the
   * current file, and XML files don't contain references that resolve to light R fields.
   */
  @WorkerThread
  @Override
  @Nullable
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    if (element instanceof ResourceReferencePsiElement) {
      return new MyFindUsagesHandler(element, PsiElement.EMPTY_ARRAY);
    }
    AndroidFacet facet = AndroidFacet.getInstance(element.getContainingFile());
    if (facet == null) {
      return null;
    }

    if (element instanceof LazyValueResourceElementWrapper) {
      ValueResourceInfo resourceInfo = ((LazyValueResourceElementWrapper)element).getResourceInfo();
      PsiField[] resourceFields = forHighlightUsages
                                  ? PsiField.EMPTY_ARRAY
                                  : AndroidResourceUtil.findResourceFields(facet, resourceInfo.getType().getName(),
                                                                           resourceInfo.getName(), true);
      return new MyFindUsagesHandler(element, resourceFields);
    }

    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element;
      if (AndroidResourceUtil.isIdDeclaration(value)) {
        element = wrapIfNecessary(value);
        PsiField[] fields = forHighlightUsages ? PsiField.EMPTY_ARRAY : AndroidResourceUtil.findIdFields(value);
        return new MyFindUsagesHandler(element, fields);
      }
    }
    element = correctResourceElement(element);
    if (element instanceof PsiFile) {
      // resource file
      PsiField[] fields = forHighlightUsages
                          ? PsiField.EMPTY_ARRAY
                          : AndroidResourceUtil.findResourceFieldsForFileResource((PsiFile)element, true);
      if (fields.length == 0) {
        return null;
      }
      return new MyFindUsagesHandler(element, fields);
    }
    else if (element instanceof XmlTag) {
      // value resource
      XmlTag tag = (XmlTag)element;
      final XmlAttribute nameAttr = tag.getAttribute(ATTR_NAME);
      final XmlAttributeValue nameValue = nameAttr != null ? nameAttr.getValueElement() : null;
      assert nameValue != null;

      PsiField[] fields = PsiField.EMPTY_ARRAY;
      if (!forHighlightUsages) {
        fields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, true);
        if (fields.length == 0) {
          return null;
        }

        PsiField[] styleableFields = AndroidResourceUtil.findStyleableAttributeFields(tag, true);
        if (styleableFields.length > 0) {
          fields = ObjectArrays.concat(fields, styleableFields, PsiField.class);
        }
      }

      return new MyFindUsagesHandler(nameValue, fields);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      List<PsiElement> resources = AndroidResourceUtil.findResourcesByField(field);
      if (resources.isEmpty()) {
        return new MyFindUsagesHandler(element);
      }

      // ignore alternative resources because their usages are the same
      PsiElement resource = resources.get(0);
      return createFindUsagesHandler(resource, forHighlightUsages);
    }
    return null;
  }
}
