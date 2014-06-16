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

import com.android.tools.idea.rendering.AppResourceRepository;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.resources.ResourceType.DECLARE_STYLEABLE;
import static com.android.resources.ResourceType.STYLEABLE;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceRenameResourceProcessor extends RenamePsiElementProcessor {
  // for tests
  public static volatile boolean ASK = true;

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final PsiElement element1 = LazyValueResourceElementWrapper.computeLazyElement(element);
        if (element1 == null) {
          return false;
        }

        if (element1 instanceof PsiFile) {
          return AndroidFacet.getInstance(element1) != null && AndroidResourceUtil.isInResourceSubdirectory((PsiFile)element1, null);
        }
        else if (element1 instanceof PsiField) {
          PsiField field = (PsiField)element1;
          if (AndroidResourceUtil.isResourceField(field)) {
            return AndroidResourceUtil.findResourcesByField(field).size() > 0;
          }
        }
        else if (element1 instanceof XmlAttributeValue) {
          LocalResourceManager manager = LocalResourceManager.getInstance(element1);
          if (manager != null) {
            if (AndroidResourceUtil.isIdDeclaration((XmlAttributeValue)element1)) {
              return true;
            }
            // then it is value resource
            XmlTag tag = PsiTreeUtil.getParentOfType(element1, XmlTag.class);
            return tag != null &&
                   DomManager.getDomManager(tag.getProject()).getDomElement(tag) instanceof ResourceElement &&
                   manager.getValueResourceType(tag) != null;
          }
        }
        else if (element1 instanceof PsiClass) {
          PsiClass cls = (PsiClass)element1;
          if (AndroidDomUtil.isInheritor(cls, CLASS_VIEW)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    final PsiElement element1 = LazyValueResourceElementWrapper.computeLazyElement(element);
    if (element1 == null) {
      return;
    }

    // TODO: support renaming alternative value resources

    AndroidFacet facet = AndroidFacet.getInstance(element1);
    assert facet != null;
    if (element1 instanceof PsiFile) {
      prepareResourceFileRenaming((PsiFile)element1, newName, allRenames, facet);
    }
    else if (element1 instanceof PsiClass) {
      PsiClass cls = (PsiClass)element1;
      if (AndroidDomUtil.isInheritor(cls, CLASS_VIEW)) {
        prepareCustomViewRenaming(cls, newName, allRenames, facet);
      }
    }
    else if (element1 instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)element1;
      if (AndroidResourceUtil.isIdDeclaration(value)) {
        prepareIdRenaming(value, newName, allRenames, facet);
      }
      else {
        prepareValueResourceRenaming(element1, newName, allRenames, facet);
      }
    }
    else if (element1 instanceof PsiField) {
      prepareResourceFieldRenaming((PsiField)element1, newName, allRenames);
    }
  }

  private static void prepareCustomViewRenaming(PsiClass cls, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    AppResourceRepository appResources = AppResourceRepository.getAppResources(facet, true);
    String oldName = cls.getName();
    if (appResources.hasResourceItem(DECLARE_STYLEABLE, oldName)) {
      LocalResourceManager manager = facet.getLocalResourceManager();
      for (PsiElement element : manager.findResourcesByFieldName(STYLEABLE.getName(), oldName)) {
        if (element instanceof XmlAttributeValue) {
          if (element.getParent() instanceof XmlAttribute) {
            XmlTag tag = ((XmlAttribute)element.getParent()).getParent();
            String tagName = tag.getName();
            if (tagName.equals(TAG_DECLARE_STYLEABLE)) {
              // Rename main styleable field
              for (PsiField field : AndroidResourceUtil.findResourceFields(facet, STYLEABLE.getName(), oldName, false)) {
                String escaped = AndroidResourceUtil.getFieldNameByResourceName(newName);
                allRenames.put(field, escaped);
              }

              // Rename dependent attribute fields
              PsiField[] styleableFields = AndroidResourceUtil.findStyleableAttributeFields(tag, false);
              if (styleableFields.length > 0) {
                for (PsiField resField : styleableFields) {
                  String fieldName = resField.getName();
                  String newAttributeName;
                  if (fieldName.startsWith(oldName)) {
                    newAttributeName = newName + fieldName.substring(oldName.length());
                  }
                  else {
                    newAttributeName = oldName;
                  }
                  String escaped = AndroidResourceUtil.getFieldNameByResourceName(newAttributeName);
                  allRenames.put(resField, escaped);
                }
              }
            }
          }
        }
      }
    }
  }

  private static void prepareIdRenaming(XmlAttributeValue value, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    LocalResourceManager manager = facet.getLocalResourceManager();
    allRenames.remove(value);
    String id = AndroidResourceUtil.getResourceNameByReferenceText(value.getValue());
    assert id != null;
    List<XmlAttributeValue> idDeclarations = manager.findIdDeclarations(id);
    for (XmlAttributeValue idDeclaration : idDeclarations) {
      // Only include explicit definitions (android:id). References through
      // these are found via the normal rename refactoring usage search in
      // RenamePsiElementProcessor#findReferences.
      //
      // And unfortunately, if we include declaration+references like
      // android:labelFor="@+id/foo", we hit an assertion from the refactoring
      // framework which looks related to elements getting modified multiple times.
      if (!ATTR_ID.equals(((XmlAttribute)idDeclaration.getParent()).getLocalName())) {
        continue;
      }

      allRenames.put(new ValueResourceElementWrapper(idDeclaration), newName);
    }

    String name = AndroidResourceUtil.getResourceNameByReferenceText(newName);
    if (name != null) {
      for (PsiField resField : AndroidResourceUtil.findIdFields(value)) {
        allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(name));
      }
    }
  }

  @Nullable
  private static String getResourceName(Project project, String newFieldName, String oldResourceName) {
    if (newFieldName.indexOf('_') < 0) return newFieldName;
    if (oldResourceName.indexOf('_') < 0 && oldResourceName.indexOf('.') >= 0) {
      String suggestion = newFieldName.replace('_', '.');
      newFieldName = Messages.showInputDialog(project, AndroidBundle.message("rename.resource.dialog.text", oldResourceName),
                                              RefactoringBundle.message("rename.title"), Messages.getQuestionIcon(), suggestion, null);
    }
    return newFieldName;
  }

  private static void prepareResourceFieldRenaming(PsiField field, String newName, Map<PsiElement, String> allRenames) {
    new RenameJavaVariableProcessor().prepareRenaming(field, newName, allRenames);
    List<PsiElement> resources = AndroidResourceUtil.findResourcesByField(field);

    PsiElement res = resources.get(0);
    String resName = res instanceof XmlAttributeValue ? ((XmlAttributeValue)res).getValue() : ((PsiFile)res).getName();
    final String newResName = getResourceName(field.getProject(), newName, resName);

    for (PsiElement resource : resources) {
      if (resource instanceof PsiFile) {
        PsiFile file = (PsiFile)resource;
        String extension = FileUtilRt.getExtension(file.getName());
        allRenames.put(resource, newResName + '.' + extension);
      }
      else if (resource instanceof XmlAttributeValue) {
        XmlAttributeValue value = (XmlAttributeValue)resource;
        final String s = AndroidResourceUtil.isIdDeclaration(value)
                         ? NEW_ID_PREFIX + newResName
                         : newResName;
        allRenames.put(new ValueResourceElementWrapper(value), s);

        // Also rename the dependent fields, e.g. if you rename <declare-styleable name="Foo">,
        // we have to rename not just R.styleable.Foo but the also R.styleable.Foo_* attributes
        if (value.getParent() instanceof XmlAttribute) {
          XmlAttribute parent = (XmlAttribute)value.getParent();
          XmlTag tag = parent.getParent();
          if (tag.getName().equals(TAG_DECLARE_STYLEABLE)) {
            AndroidFacet facet = AndroidFacet.getInstance(tag);
            String oldName = tag.getAttributeValue(ATTR_NAME);
            if (facet != null && oldName != null) {
              for (XmlTag attr : tag.getSubTags()) {
                if (attr.getName().equals(TAG_ATTR)) {
                  String name = attr.getAttributeValue(ATTR_NAME);
                  if (name != null) {
                    String oldAttributeName = oldName + '_' + name;
                    PsiField[] fields = AndroidResourceUtil.findResourceFields(facet, STYLEABLE.getName(), oldAttributeName, true);
                    if (fields.length > 0) {
                      String newAttributeName = newName + '_' + name;
                      for (PsiField f : fields) {
                        allRenames.put(f, newAttributeName);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private static void prepareValueResourceRenaming(PsiElement element,
                                                   String newName,
                                                   Map<PsiElement, String> allRenames,
                                                   AndroidFacet facet) {
    ResourceManager manager = facet.getLocalResourceManager();
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    assert tag != null;
    String type = manager.getValueResourceType(tag);
    assert type != null;
    Project project = tag.getProject();
    DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
    assert domElement instanceof ResourceElement;
    String name = ((ResourceElement)domElement).getName().getValue();
    assert name != null;
    List<ResourceElement> resources = manager.findValueResources(type, name);
    for (ResourceElement resource : resources) {
      XmlElement xmlElement = resource.getName().getXmlAttributeValue();
      if (!element.getManager().areElementsEquivalent(element, xmlElement)) {
        allRenames.put(xmlElement, newName);
      }
    }
    PsiField[] resFields = AndroidResourceUtil.findResourceFieldsForValueResource(tag, false);
    for (PsiField resField : resFields) {
      String escaped = AndroidResourceUtil.getFieldNameByResourceName(newName);
      allRenames.put(resField, escaped);
    }

    // Also rename the dependent fields, e.g. if you rename <declare-styleable name="Foo">,
    // we have to rename not just R.styleable.Foo but the also R.styleable.Foo_* attributes
    PsiField[] styleableFields = AndroidResourceUtil.findStyleableAttributeFields(tag, false);
    if (styleableFields.length > 0) {
      String tagName = tag.getName();
      boolean isDeclareStyleable = tagName.equals(TAG_DECLARE_STYLEABLE);
      boolean isAttr = !isDeclareStyleable && tagName.equals(TAG_ATTR) && tag.getParentTag() != null;
      assert isDeclareStyleable || isAttr;
      String style = isAttr ? tag.getParentTag().getAttributeValue(ATTR_NAME) : null;
      for (PsiField resField : styleableFields) {
        String fieldName = resField.getName();
        String newAttributeName;
        if (isDeclareStyleable && fieldName.startsWith(name)) {
          newAttributeName = newName + fieldName.substring(name.length());
        }
        else if (isAttr && style != null) {
          newAttributeName = style + '_' + newName;
        }
        else {
          newAttributeName = name;
        }
        String escaped = AndroidResourceUtil.getFieldNameByResourceName(newAttributeName);
        allRenames.put(resField, escaped);
      }
    }
  }

  private static void prepareResourceFileRenaming(PsiFile file, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    Project project = file.getProject();
    ResourceManager manager = facet.getLocalResourceManager();
    String type = manager.getFileResourceType(file);
    if (type == null) return;
    String name = file.getName();

    if (AndroidCommonUtils.getResourceName(type, name).equals(AndroidCommonUtils.getResourceName(type, newName))) {
      return;
    }

    List<PsiFile> resourceFiles = manager.findResourceFiles(type, AndroidCommonUtils.getResourceName(type, name), true, false);
    List<PsiFile> alternativeResources = new ArrayList<PsiFile>();
    for (PsiFile resourceFile : resourceFiles) {
      if (!resourceFile.getManager().areElementsEquivalent(file, resourceFile) && resourceFile.getName().equals(name)) {
        alternativeResources.add(resourceFile);
      }
    }
    if (alternativeResources.size() > 0) {
      int r = 0;
      if (ASK) {
        r = Messages.showDialog(project, message("rename.alternate.resources.question"), message("rename.dialog.title"),
                                new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 1, Messages.getQuestionIcon());
      }
      if (r == 0) {
        for (PsiFile candidate : alternativeResources) {
          allRenames.put(candidate, newName);
        }
      }
      else {
        return;
      }
    }
    PsiField[] resFields = AndroidResourceUtil.findResourceFieldsForFileResource(file, false);
    for (PsiField resField : resFields) {
      String newFieldName = AndroidCommonUtils.getResourceName(type, newName);
      allRenames.put(resField, AndroidResourceUtil.getFieldNameByResourceName(newFieldName));
    }
  }

  @Override
  public void renameElement(PsiElement element, final String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    if (element instanceof PsiField) {
      new RenameJavaVariableProcessor().renameElement(element, newName, usages, listener);
    }
    else {
      if (element instanceof PsiNamedElement) {
        super.renameElement(element, newName, usages, listener);

        if (element instanceof PsiFile) {
          VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
          if (virtualFile != null && !LocalHistory.getInstance().isUnderControl(virtualFile)) {
            DocumentReference ref = DocumentReferenceManager.getInstance().create(virtualFile);
            UndoManager.getInstance(element.getProject()).nonundoableActionPerformed(ref, false);
          }
        }
      }
      else if (element instanceof XmlAttributeValue) {
        new RenameXmlAttributeProcessor().renameElement(element, newName, usages, listener);
      }
    }
  }
}
