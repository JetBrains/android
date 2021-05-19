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

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.resources.ResourceType.ATTR;
import static com.android.resources.ResourceType.STYLEABLE;
import static com.android.resources.ResourceType.STYLE_ITEM;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.ide.common.gradle.model.IdeLibrary;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.util.PathString;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.psi.ResourceReferencePsiElement;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameXmlAttributeProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.dom.resources.Attr;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidResourceRenameResourceProcessor extends RenamePsiElementProcessor {
  private static final Ordering<String> ORDER_BY_LENGTH = new Ordering<String>() {
    @Override
    public int compare(String left, String right) {
      int lengthCompare = Ints.compare(left.length(), right.length());

      return lengthCompare != 0 ? lengthCompare : StringUtil.compare(left, right, true);
    }
  };

  // for tests
  public static volatile boolean ASK = true;

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    if (StudioFlags.RESOLVE_USING_REPOS.get()) {
      return false;
    }
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      final PsiElement computedElement = LazyValueResourceElementWrapper.computeLazyElement(element);
      if (computedElement == null) {
        return false;
      }

      if (computedElement instanceof PsiFile) {
        return AndroidFacet.getInstance(computedElement) != null
               && IdeResourcesUtil.isInResourceSubdirectory((PsiFile)computedElement, null);
      }
      else if (computedElement instanceof PsiField) {
        PsiField field = (PsiField)computedElement;
        if (IdeResourcesUtil.isResourceField(field)) {
          return !IdeResourcesUtil.findResourcesByField(field).isEmpty();
        }
      }
      else if (computedElement instanceof ResourceFieldElementWrapper) {
        return true;
      }
      else if (computedElement instanceof XmlAttributeValue) {
        LocalResourceManager manager = LocalResourceManager.getInstance(computedElement);
        if (manager != null) {
          if (IdeResourcesUtil.isIdDeclaration((XmlAttributeValue)computedElement)) {
            return true;
          }
          // then it is value resource
          XmlTag tag = PsiTreeUtil.getParentOfType(computedElement, XmlTag.class);
          return tag != null &&
                 ResourcesDomFileDescription.isResourcesFile((XmlFile)computedElement.getContainingFile()) &&
                 manager.getValueResourceType(tag) != null;
        }
      }
      else if (computedElement instanceof PsiClass) {
        PsiClass cls = (PsiClass)computedElement;
        if (InheritanceUtil.isInheritor(cls, CLASS_VIEW)) {
          return true;
        }
      }
      return false;
    });
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    if (element instanceof AndroidLightField) {
      return new ResourceFieldElementWrapper((AndroidLightField)element);
    }

    return super.substituteElementToRename(element, editor);
  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    final PsiElement computedElement = LazyValueResourceElementWrapper.computeLazyElement(element);
    if (computedElement == null) {
      return;
    }

    // TODO: support renaming alternative value resources

    AndroidFacet facet = AndroidFacet.getInstance(computedElement);
    assert facet != null;
    if (computedElement instanceof PsiFile) {
      prepareResourceFileRenaming((PsiFile)computedElement, newName, allRenames, facet);
    }
    else if (computedElement instanceof PsiClass) {
      PsiClass cls = (PsiClass)computedElement;
      if (InheritanceUtil.isInheritor(cls, CLASS_VIEW)) {
        prepareCustomViewRenaming(cls, newName, allRenames, facet);
      }
    }
    else if (computedElement instanceof XmlAttributeValue) {
      XmlAttributeValue value = (XmlAttributeValue)computedElement;
      if (IdeResourcesUtil.isIdDeclaration(value)) {
        if (IdeResourcesUtil.isIdDeclaration(newName)) {
          prepareIdRenaming(value, newName, allRenames, facet);
        }
      }
      else {
        prepareValueResourceRenaming(computedElement, newName, allRenames, facet);
      }
    }
    else if (computedElement instanceof PsiField) {
      prepareResourceFieldRenaming((PsiField)computedElement, newName, allRenames);
    }
    else if (computedElement instanceof ResourceFieldElementWrapper) {
      prepareResourceFieldRenaming(((ResourceFieldElementWrapper)computedElement).getWrappedElement(), newName, allRenames);
    }
    else if (computedElement instanceof ResourceReferencePsiElement) {
      prepareValueResourceRenaming(computedElement, newName, allRenames, facet);
    }
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback(@NotNull PsiElement element,
                                        @NotNull String newName,
                                        @NotNull RefactoringElementListener elementListener) {
    // After renaming, we need to wait for the new resource to be propagated through the resource repositories. After that we need the
    // resolve caches to be invalidated and the highlighting to be triggered again.
    PsiManager manager = PsiManager.getInstance(element.getProject());
    return () -> IdeResourcesUtil.scheduleNewResolutionAndHighlighting(manager);
  }

  private static void prepareCustomViewRenaming(PsiClass cls, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(facet);
    String oldName = cls.getName();
    if (appResources.hasResources(ResourceNamespace.TODO(), STYLEABLE, oldName)) {
      LocalResourceManager manager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
      for (PsiElement element : manager.findResourcesByFieldName(ResourceNamespace.TODO(), STYLEABLE.getName(), oldName)) {
        if (element instanceof XmlAttributeValue) {
          if (element.getParent() instanceof XmlAttribute) {
            XmlTag tag = ((XmlAttribute)element.getParent()).getParent();
            String tagName = tag.getName();
            if (tagName.equals(TAG_DECLARE_STYLEABLE)) {
              // Rename main styleable field
              for (PsiField field : IdeResourcesUtil.findResourceFields(facet, STYLEABLE.getName(), oldName, false)) {
                String escaped = IdeResourcesUtil.getFieldNameByResourceName(newName);
                allRenames.put(field, escaped);
              }

              // Rename dependent attribute fields
              PsiField[] styleableFields = IdeResourcesUtil.findStyleableAttributeFields(tag, false);
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
                  String escaped = IdeResourcesUtil.getFieldNameByResourceName(newAttributeName);
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
    allRenames.remove(value);
    LocalResourceManager manager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    String id = IdeResourcesUtil.getResourceNameByReferenceText(value.getValue());
    assert id != null;
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(facet);
    ResourceNamespace namespace = repositoryManager.getNamespace();
    List<XmlAttributeValue> idDeclarations = manager.findIdDeclarations(namespace, id);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(facet.getModule().getProject()).getFileIndex();
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

      // Include the element in refactoring only if it is a part of the project.
      if (fileIndex.isInContent(idDeclaration.getContainingFile().getVirtualFile())) {
        allRenames.put(new ValueResourceElementWrapper(idDeclaration), newName);
      }
    }

    String name = IdeResourcesUtil.getResourceNameByReferenceText(newName);
    if (name != null) {
      for (PsiField resField : IdeResourcesUtil.findIdFields(value)) {
        allRenames.put(resField, IdeResourcesUtil.getFieldNameByResourceName(name));
      }
    }

    // Rename the id in app:constraint_referenced_ids attribute, which contains a list of id and use comma as separator.
    List<XmlAttributeValue> usages = manager.findConstraintReferencedIds(namespace, id);
    for (XmlAttributeValue usage : usages) {
      // Include the element in refactoring only if it is a part of the project.
      if (fileIndex.isInContent(usage.getContainingFile().getVirtualFile())) {
        String newId = IdeResourcesUtil.getResourceNameByReferenceText(newName);
        String oldValue = usage.getValue();
        if (oldValue != null) {
          String newValue = Arrays.stream(oldValue.split(",")).map(s -> id.equals(s) ? newId : s).collect(Collectors.joining(","));
          allRenames.put(new ValueResourceElementWrapper(usage), newValue);
        }
      }
    }
  }

  @Nullable
  private static String getResourceName(Project project, String newFieldName, String oldResourceName) {
    if (newFieldName.indexOf('_') < 0) return newFieldName;
    if (oldResourceName.indexOf('_') < 0 && oldResourceName.indexOf('.') >= 0) {
      String suggestion = newFieldName.replace('_', '.');
      newFieldName = Messages.showInputDialog(project, message("rename.resource.dialog.text", oldResourceName),
                                              RefactoringBundle.message("rename.title"), Messages.getQuestionIcon(), suggestion, null);
    }
    return newFieldName;
  }

  private static void prepareResourceFieldRenaming(PsiField field, String newName, Map<PsiElement, String> allRenames) {
    new RenameJavaVariableProcessor().prepareRenaming(field, newName, allRenames);

    List<PsiElement> resources = IdeResourcesUtil.findResourcesByField(field);

    PsiElement res = resources.get(0);

    final String newResName;
    if (res instanceof PsiFile) {
      // Resource comes from XML file, don't need to suggest change underscores to dots
      newResName = newName;
    }
    else if (res instanceof XmlAttributeValue){
      newResName = getResourceName(field.getProject(), newName, ((XmlAttributeValue)res).getValue());
    }
    else {
      // AndroidResourcesIdeUtil.findResourcesByField supposed to return a list of PsiElements that are
      // either PsiFile or XmlAttributeValue. Previous version of this code doesn't handle other
      // possibilities at all and would crash with ClassCastException, having an explicit error message
      // seems to be a slightly better option.
      Logger.getInstance(AndroidResourceRenameResourceProcessor.class).error(
        String.format("%s: res is neither PsiFile nor XmlAttributeValue", AndroidResourceRenameResourceProcessor.class.getSimpleName()));
      newResName = newName;
    }

    for (PsiElement resource : resources) {
      if (resource instanceof PsiFile) {
        PsiFile file = (PsiFile)resource;
        String extension = FileUtilRt.getExtension(file.getName());
        allRenames.put(resource, newResName + '.' + extension);
      }
      else if (resource instanceof XmlAttributeValue) {
        XmlAttributeValue value = (XmlAttributeValue)resource;
        final String s = IdeResourcesUtil.isIdDeclaration(value)
                         ? NEW_ID_PREFIX + newResName
                         : newResName;
        String className = IdeResourcesUtil.getResourceClassName(field);
        if (className != null) {
          ResourceType type = ResourceType.fromClassName(className);
          // Attr and StyleItems should not be added to the allRenames list as their name attributes resolve to ResourceReferencePsiElement
          // and will be found in usages.
          if (!ATTR.equals(type) && !STYLE_ITEM.equals(type)) {
            allRenames.put(new ValueResourceElementWrapper(value), s);
          }
        }

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
                    PsiField[] fields = IdeResourcesUtil.findResourceFields(facet, STYLEABLE.getName(), oldAttributeName, true);
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
                                                   final AndroidFacet facet) {
    LocalResourceManager localResourceManager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    ResourceRepositoryManager repoManager = ResourceRepositoryManager.getInstance(facet);
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    assert tag != null;
    ResourceType type = localResourceManager.getValueResourceType(tag);
    assert type != null;
    Project project = tag.getProject();
    DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);
    assert domElement instanceof ResourceElement || domElement instanceof Attr;
    String resourceName;
    if (domElement instanceof ResourceElement) {
      resourceName = ((ResourceElement)domElement).getName().getValue();
    } else {
      ResourceReference resourceReference = ((Attr)domElement).getName().getValue();
      if (resourceReference == null) {
        return;
      }
      resourceName = resourceReference.getName();
    }
    if (resourceName == null) {
      return;
    }

    List<ResourceElement> resources = localResourceManager.findValueResources(ResourceNamespace.TODO(), type.getName(), resourceName);
    for (ResourceElement resource : resources) {
      XmlElement xmlElement = resource.getName().getXmlAttributeValue();
      if (!element.getManager().areElementsEquivalent(element, xmlElement)) {
        allRenames.put(xmlElement, newName);
      }
    }

    if (getResourceType(element) == ResourceType.STYLE) {
      // For styles, try also to find child styles defined by name (i.e. "ParentName.StyleName") and add them
      // to the rename list. This will allow the rename processor to also handle the references to those. For example,
      // If you rename "MyTheme" and your manifest theme is "MyTheme.NoActionBar", this will make sure that
      // the reference from the manifest is also updated by adding "MyTheme.NoActionBar" to the rename list.
      // We iterate the styles in order to cascade any changes to children down the hierarchy.

      // List of styles that will be renamed.
      HashSet<String> renamedStyles = new HashSet<>();
      renamedStyles.add(resourceName);

      final String stylePrefix = resourceName + ".";
      Collection<String> renameCandidates;
      Collection<String> allStyles = repoManager.getAppResources().getResources(ResourceNamespace.TODO(), type).keySet();
      renameCandidates = Collections2.filter(allStyles, styleName -> styleName.startsWith(stylePrefix));

      for (String renameCandidate : ORDER_BY_LENGTH.sortedCopy(renameCandidates)) {
        // renameCandidate.lastIndexOf will never return -1 because we've filtered all names that
        // do not contain stylePrefix
        String parentName = renameCandidate.substring(0, renameCandidate.lastIndexOf('.'));
        if (!renamedStyles.contains(parentName)) {
          // This resource's parent wasn't affected by the rename
          continue;
        }

        for (ResourceElement resource : localResourceManager.findValueResources(ResourceNamespace.TODO(), type.getName(), renameCandidate)) {
          if (!(resource instanceof Style) || ((Style)resource).getParentStyle().getXmlAttributeValue() != null) {
            // This element is not a style or does have an explicit parent so we do not rename it.
            continue;
          }

          XmlAttributeValue xmlElement = resource.getName().getXmlAttributeValue();

          if (xmlElement != null) {
            String newStyleName = newName + StringUtil.trimStart(renameCandidate, resourceName);
            allRenames.put(new ValueResourceElementWrapper(xmlElement), newStyleName);
            renamedStyles.add(renameCandidate);
          }
        }
      }
    }

    PsiField[] resFields = IdeResourcesUtil.findResourceFieldsForValueResource(tag, false);
    for (PsiField resField : resFields) {
      String escaped = IdeResourcesUtil.getFieldNameByResourceName(newName);
      allRenames.put(resField, escaped);
    }

    // Also rename the dependent fields, e.g. if you rename <declare-styleable name="Foo">,
    // we have to rename not just R.styleable.Foo but the also R.styleable.Foo_* attributes
    PsiField[] styleableFields = IdeResourcesUtil.findStyleableAttributeFields(tag, false);
    if (styleableFields.length > 0) {
      String tagName = tag.getName();
      boolean isDeclareStyleable = tagName.equals(TAG_DECLARE_STYLEABLE);
      boolean isAttr = !isDeclareStyleable && tagName.equals(TAG_ATTR) && tag.getParentTag() != null;
      assert isDeclareStyleable || isAttr;
      String style = isAttr ? tag.getParentTag().getAttributeValue(ATTR_NAME) : null;
      for (PsiField resField : styleableFields) {
        String fieldName = resField.getName();
        String newAttributeName;
        if (isDeclareStyleable && fieldName.startsWith(resourceName)) {
          newAttributeName = newName + fieldName.substring(resourceName.length());
        }
        else if (isAttr && style != null) {
          newAttributeName = style + '_' + newName;
        }
        else {
          newAttributeName = resourceName;
        }
        String escaped = IdeResourcesUtil.getFieldNameByResourceName(newAttributeName);
        allRenames.put(resField, escaped);
      }
    }
  }

  private static void prepareResourceFileRenaming(PsiFile file, String newName, Map<PsiElement, String> allRenames, AndroidFacet facet) {
    Project project = file.getProject();
    LocalResourceManager manager = ModuleResourceManagers.getInstance(facet).getLocalResourceManager();
    ResourceFolderType type = manager.getFileResourceFolderType(file);
    if (type == null) return;
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(file.getName());
    String resourceName = SdkUtils.fileNameToResourceName(file.getName());

    if (SdkUtils.fileNameToResourceName(nameWithoutExtension).equals(SdkUtils.fileNameToResourceName(newName))) {
      return;
    }

    Collection<PsiFile> resourceFiles = manager.findResourceFiles(ResourceNamespace.TODO(), type, resourceName, true, false);
    List<PsiFile> alternativeResources = new ArrayList<>();
    for (PsiFile resourceFile : resourceFiles) {
      String alternativeFileName = SdkUtils.fileNameToResourceName(resourceFile.getName());
      if (!resourceFile.getManager().areElementsEquivalent(file, resourceFile) && alternativeFileName.equals(resourceName)) {
        alternativeResources.add(resourceFile);
      }
    }
    if (!alternativeResources.isEmpty()) {
      int r = 0;
      if (ASK) {
        r = Messages.showDialog(project, message("rename.alternate.resources.question"), message("rename.dialog.title"),
                                new String[]{Messages.getYesButton(), Messages.getNoButton()}, 1, Messages.getQuestionIcon());
      }
      if (r == 0) {
        for (PsiFile candidate : alternativeResources) {
          String extension = FileUtilRt.getExtension(candidate.getName());
          if (!extension.isEmpty()) {
            extension = "." + extension;
          }
          allRenames.put(candidate, FileUtil.getNameWithoutExtension(newName) + extension);
        }
      }
      else {
        return;
      }
    }
    PsiField[] resFields = IdeResourcesUtil.findResourceFieldsForFileResource(file, false);
    for (PsiField resField : resFields) {
      String newFieldName = SdkUtils.fileNameToResourceName(newName);
      allRenames.put(resField, IdeResourcesUtil.getFieldNameByResourceName(newFieldName));
    }
  }

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                 @NotNull SearchScope searchScope,
                                                 boolean searchInCommentsAndStrings) {
    if (element instanceof ResourceFieldElementWrapper) {
      element = ((ResourceFieldElementWrapper)element).getWrappedElement();
    }
    return super.findReferences(element, searchScope, searchInCommentsAndStrings);
  }

  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull final String newName, @NotNull UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    if (element instanceof PsiField) {
      new RenameJavaVariableProcessor().renameElement(element, newName, usages, listener);
    }
    else if (element instanceof ResourceFieldElementWrapper) {
      new RenameJavaVariableProcessor().renameElement(((ResourceFieldElementWrapper)element).getWrappedElement(), newName, usages, listener);
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

  @Override
  public void findExistingNameConflicts(@NotNull final PsiElement originalElement, @NotNull String newName, @NotNull final MultiMap<PsiElement,String> conflicts) {
    ResourceType type = getResourceType(originalElement);
    if (type == null) {
      return;
    }

    PsiElement element = LazyValueResourceElementWrapper.computeLazyElement(originalElement);
    if (element == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(element);
    if (facet == null) {
      return;
    }

    // First check to see if the new name is conflicting with an existing resource
    if (element instanceof PsiFile) {
      // The name of a file resource is the name of the file without the extension.
      // So when dealing with a file, we must first remove the extension in the name
      // before checking if it is already used.
      newName = SdkUtils.fileNameToResourceName(newName);
    }
    LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(facet);
    if (appResources.hasResources(ResourceNamespace.TODO(), type, newName)) {
      boolean foundElements = false;
      PsiField[] resourceFields = IdeResourcesUtil.findResourceFields(facet, type.getName(), newName, true);
      String message = String.format("Resource @%1$s/%2$s already exists", type, newName);
      if (resourceFields.length > 0) {
        // Use find usages to find the actual declaration location such that they can be shown in the conflicts view
        AndroidFindUsagesHandlerFactory factory = new AndroidFindUsagesHandlerFactory();
        if (factory.canFindUsages(originalElement)) {
          FindUsagesHandler handler = factory.createFindUsagesHandler(resourceFields[0], false);
          if (handler != null) {
            PsiElement[] elements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
            for (PsiElement e : elements) {
              if (e instanceof LightElement) { // AndroidLightField does not work in the conflicts view; UsageInfo throws NPE
                continue;
              }
              conflicts.putValue(e, message);
              foundElements = true;
            }
          }
        }
      }

      if (!foundElements) {
        conflicts.putValue(originalElement, message);
      }
    }

    // Next see if the renamed resource is also defined externally, in which case we should ask the
    // user if they really want to continue. Despite the name of this method ("findExistingNameConflicts")
    // and the dialog used to show the message (ConflictsDialog), this isn't conflict specific; the
    // dialog title simply says "Problems Detected" and the label for the text view is "The following
    // problems were found". We need to use this because it's the only facility in the rename processor
    // which lets us ask the user whether to continue and to have the answer either bail out of the operation
    // or to resume.
    // See if this is a locally defined resource (you can't rename fields from libraries such as appcompat)
    // e.g. ?attr/listPreferredItemHeightSmall
    String name = getResourceName(originalElement);
    if (name != null) {
      Project project = facet.getModule().getProject();
      List<ResourceItem> all = appResources.getResources(ResourceNamespace.TODO(), type, name);
      List<ResourceItem> local =
          ResourceRepositoryManager.getProjectResources(facet).getResources(ResourceNamespace.TODO(), type, name);
      HtmlBuilder builder = null;
      if (local.isEmpty() && !all.isEmpty()) {
        builder = new HtmlBuilder(new StringBuilder(300));
        builder.add("Resource is also only defined in external libraries and cannot be renamed.");
      }
      else if (local.size() < all.size()) {
        // This item is also defined in one of the libraries, not just locally: we can't rename it. Should we
        // display some sort of warning?
        builder = new HtmlBuilder(new StringBuilder(300));
        builder.add("The resource ").beginBold().add(PREFIX_RESOURCE_REF).add(type.getName()).add("/").add(name).endBold();
        builder.add(" is defined outside of the project (in one of the libraries) and cannot ");
        builder.add("be updated. This can change the behavior of the application.").newline().newline();
        builder.add("Are you sure you want to do this?");
      }
      if (builder != null) {
        appendUnhandledReferences(project, facet, all, local, builder);
        conflicts.putValue(originalElement, builder.getHtml());
      }
    }
  }

  /** Looks up the {@link ResourceType} for the given refactored element. Uses the same
   * instanceof chain checkups as is done in {@link #canProcessElement} */
  @Nullable
  private static ResourceType getResourceType(PsiElement originalElement) {
    PsiElement element = LazyValueResourceElementWrapper.computeLazyElement(originalElement);
    if (element == null) {
      return null;
    }

    if (element instanceof PsiFile) {
      ResourceFolderType folderType = IdeResourcesUtil.getFolderType((PsiFile)element);
      if (folderType != null && folderType != ResourceFolderType.VALUES) {
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        if (!types.isEmpty()) {
          return types.get(0);
        }
      }
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      if (IdeResourcesUtil.isResourceField(field)) {
        return ResourceType.fromClassName(IdeResourcesUtil.getResourceClassName(field));
      }
    }
    else if (element instanceof XmlAttributeValue) {
      LocalResourceManager manager = LocalResourceManager.getInstance(element);
      if (manager != null) {
        if (IdeResourcesUtil.isIdDeclaration((XmlAttributeValue)element)) {
          return ResourceType.ID;
        }
        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag != null && DomManager.getDomManager(tag.getProject()).getDomElement(tag) instanceof ResourceElement) {
          return IdeResourcesUtil.getResourceTypeForResourceTag(tag);
        }
      }
    }

    return null;
  }

  /** Looks up the resource name for the given refactored element. Uses the same
   * instanceof chain checkups as is done in {@link #canProcessElement} */
  @Nullable
  private static String getResourceName(PsiElement originalElement) {
    PsiElement element = LazyValueResourceElementWrapper.computeLazyElement(originalElement);
    if (element == null) {
      return null;
    }

    if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;
      LocalResourceManager manager = LocalResourceManager.getInstance(element);
      if (manager != null) {
        String type = manager.getFileResourceType(file);
        if (type != null) {
          String name = file.getName();
          return SdkUtils.fileNameToResourceName(name);
        }
      }
      return Lint.getBaseName(file.getName());
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      return field.getName();
    }
    else if (element instanceof XmlAttributeValue) {
      if (IdeResourcesUtil.isIdDeclaration((XmlAttributeValue)element)) {
        return IdeResourcesUtil.getResourceNameByReferenceText(((XmlAttributeValue)element).getValue());
      }
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag != null) {
        DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement) {
          return ((ResourceElement)domElement).getName().getValue();
        } else if (domElement instanceof Attr) {
          ResourceReference resourceReference = ((Attr)domElement).getName().getValue();
          if (resourceReference != null) {
            return resourceReference.getName();
          }
        }
      }
    }

    return null;
  }

  /** Writes into the given {@link HtmlBuilder} a set of references
   * that are defined in a library (and may or may not also be defined locally) */
  private static void appendUnhandledReferences(@NotNull Project project,
                                                @NotNull AndroidFacet facet,
                                                @NotNull List<ResourceItem> all,
                                                @NotNull List<ResourceItem> local,
                                                @NotNull HtmlBuilder builder) {
    File root = VfsUtilCore.virtualToIoFile(project.getBaseDir());
    Collection<IdeLibrary> libraries = null;
    // Write a set of descriptions to library references. Put them in a list first such that we can
    // sort the (to for example make test output stable.)
    List<String> descriptions = new ArrayList<>();
    for (ResourceItem item : all) {
      if (!local.contains(item)) {
        if (libraries == null) {
          libraries = ResourceRepositoryManager.findAarLibraries(facet);
        }
        PathString source = item.getSource();
        if (source != null) {
          File sourceFile = source.toFile();
          if (sourceFile != null) {
            // TODO: Look up the corresponding AAR artifact, and then use library.getRequestedCoordinates() or
            // library.getResolvedCoordinates() here and append the coordinate. However, until b.android.com/77341
            // is fixed this doesn't work.
            /*
            // Attempt to find the corresponding AAR artifact
            AndroidLibrary library = null;
            for (AndroidLibrary l : libraries) {
              File res = l.getResFolder();
              if (res.exists() && FileUtil.isAncestor(res, sourceFile, true)) {
                library = l;
                break;
              }
            }
            */

            // Look for exploded-aar and strip off the prefix path to it
            File localRoot = root;
            File prev = sourceFile;
            File current = sourceFile.getParentFile();
            while (current != null) {
              String name = current.getName();
              if (FilenameConstants.EXPLODED_AAR.equals(name)) {
                localRoot = prev;
                break;
              }
              prev = current;
              current = current.getParentFile();
            }

            if (FileUtil.isAncestor(localRoot, sourceFile, true)) {
              descriptions.add(FileUtil.getRelativePath(localRoot, sourceFile));
            }
            else {
              descriptions.add(sourceFile.getPath());
            }
          }
        }
      }
    }

    Collections.sort(descriptions);

    builder.newline().newline();
    builder.add("Unhandled references:");
    builder.newline();
    int count = 0;
    for (String s : descriptions) {
      builder.add(s).newline();

      count++;
      if (count == 10) {
        builder.add("...").newline();
        builder.add("(Additional results truncated)");
        break;
      }
    }
  }
}
