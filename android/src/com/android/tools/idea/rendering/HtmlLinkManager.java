/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.SparseArray;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.compiler.actions.CompileDirtyAction;
import com.intellij.ide.browsers.UrlOpener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.android.inspections.lint.SuppressLintIntentionAction;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

import static com.android.SdkConstants.*;

public class HtmlLinkManager {
  private static final String URL_EDIT_CLASSPATH = "action:classpath";
  private static final String URL_BUILD = "action:build";
  private static final String URL_SHOW_XML = "action:showXml";
  private static final String URL_ACTION_IGNORE_FRAGMENTS = "action:ignoreFragment";
  private static final String URL_RUNNABLE = "runnable:";
  private static final String URL_COMMAND = "command:";
  private static final String URL_REPLACE_TAGS = "replaceTags:";
  private static final String URL_SHOW_TAG = "showTag:";
  private static final String URL_OPEN = "open:";
  private static final String URL_CREATE_CLASS = "createClass:";
  private static final String URL_OPEN_CLASS = "openClass:";
  private static final String URL_ASSIGN_FRAGMENT_URL = "assignFragmentUrl:";
  private static final String URL_ASSIGN_LAYOUT_URL = "assignLayoutUrl:";
  private static final String URL_EDIT_ATTRIBUTE = "editAttribute:";
  private static final String URL_REPLACE_ATTRIBUTE_VALUE = "replaceAttributeValue:";
  static final String URL_ACTION_CLOSE = "action:close";

  private SparseArray<Runnable> myLinkRunnables;
  private SparseArray<WriteCommandAction> myLinkCommands;
  private int myNextLinkId = 0;

  public HtmlLinkManager() {
  }

  public void handleUrl(@NotNull String url, @Nullable Module module, @Nullable PsiFile file, @Nullable DataContext dataContext,
                        @Nullable RenderResult result) {
    if (url.startsWith("http:")) {
      UrlOpener.launchBrowser(null, url);
    } else if (url.startsWith(URL_REPLACE_TAGS)) {
      assert module != null;
      assert file != null;
      handleReplaceTagsUrl(url, module, file);
    } else if (url.equals(URL_BUILD)) {
      assert dataContext != null;
      handleCompileModuleUrl(url, dataContext);
    } else if (url.equals(URL_EDIT_CLASSPATH)) {
      assert module != null;
      handleEditClassPathUrl(url, module);
    } else if (url.startsWith(URL_CREATE_CLASS)) {
      assert module != null && file != null;
      handleNewClassUrl(url, module);
    } else if (url.startsWith(URL_OPEN)) {
      assert module != null;
      handleOpenStackUrl(url, module);
    } else if (url.startsWith(URL_OPEN_CLASS)) {
      assert module != null;
      handleOpenClassUrl(url, module);
    } else if (url.equals(URL_SHOW_XML)) {
      assert module != null && file != null;
      handleShowXmlUrl(url, module, file);
    } else if (url.startsWith(URL_SHOW_TAG)) {
      assert module != null && file != null;
      handleShowTagUrl(url, module, file);
    } else if (url.startsWith(URL_ASSIGN_FRAGMENT_URL)) {
      assert module != null && file != null;
      handleAssignFragmentUrl(url, module, file);
    } else if (url.startsWith(URL_ASSIGN_LAYOUT_URL)) {
      assert module != null && file != null;
      handleAssignLayoutUrl(url, module, file);
    } else if (url.equals(URL_ACTION_IGNORE_FRAGMENTS)) {
      assert result != null;
      handleIgnoreFragments(url, result);
    } else if (url.startsWith(URL_EDIT_ATTRIBUTE)) {
      assert result != null;
      handleEditAttribute(url, module, file);
    } else if (url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE)) {
      assert result != null;
      handleReplaceAttributeValue(url, module, file);
    } else if (url.startsWith(URL_RUNNABLE)) {
      Runnable linkRunnable = getLinkRunnable(url);
      if (linkRunnable != null) {
        linkRunnable.run();
      }
    } else if (url.startsWith(URL_COMMAND)) {
      WriteCommandAction command = getLinkCommand(url);
      if (command != null) {
        command.execute();
      }
    } else {
      assert false : "Unexpected URL: " + url;
    }
  }
  public String createCommandLink(@NotNull WriteCommandAction command) {
    String url = URL_COMMAND + myNextLinkId;
    if (myLinkCommands == null) {
      myLinkCommands = new SparseArray<WriteCommandAction>(5);
    }
    myLinkCommands.put(myNextLinkId, command);
    myNextLinkId++;
    return url;
  }

  @Nullable
  private WriteCommandAction getLinkCommand(String url) {
    if (myLinkCommands != null && url.startsWith(URL_COMMAND)) {
      String idString = url.substring(URL_COMMAND.length());
      int id = Integer.decode(idString);
      return myLinkCommands.get(id);
    }
    return null;
  }

  public String createRunnableLink(@NotNull Runnable runnable) {
    String url = URL_RUNNABLE + myNextLinkId;
    if (myLinkRunnables == null) {
      myLinkRunnables = new SparseArray<Runnable>(5);
    }
    myLinkRunnables.put(myNextLinkId, runnable);
    myNextLinkId++;

    return url;
  }

  @Nullable
  private Runnable getLinkRunnable(String url) {
    if (myLinkRunnables != null && url.startsWith(URL_RUNNABLE)) {
      String idString = url.substring(URL_RUNNABLE.length());
      int id = Integer.decode(idString);
      return myLinkRunnables.get(id);
    }
    return null;
  }

  public String createReplaceTagsUrl(String from, String to) {
    return URL_REPLACE_TAGS + from + '/' + to;
  }

  private static void handleReplaceTagsUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.startsWith(URL_REPLACE_TAGS) : url;
    int start = URL_REPLACE_TAGS.length();
    int delimiterPos = url.indexOf('/', start);
    if (delimiterPos != -1) {
      String wrongTag = url.substring(start, delimiterPos);
      String rightTag = url.substring(delimiterPos + 1);
      ReplaceTagFix fix = new ReplaceTagFix(module.getProject(), (XmlFile)file, wrongTag, rightTag);
      fix.execute();
    }
  }

  public String createCompileModuleUrl() {
    return URL_BUILD;
  }

  private static void handleCompileModuleUrl(@NotNull String url, @NotNull DataContext dataContext) {
    assert url.equals(URL_BUILD) : url;
    // For full compilation: CompilerManager.getInstance(myResult.getModule().getProject()).rebuild(null);
    CompileDirtyAction action = new CompileDirtyAction();
    @SuppressWarnings("ConstantConditions")
    AnActionEvent actionEvent = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, action.getTemplatePresentation(),
                                                  ActionManager.getInstance(), 0);
    action.actionPerformed(actionEvent);
  }

  public String createEditClassPathUrl() {
    return URL_EDIT_CLASSPATH;
  }

  private static void handleEditClassPathUrl(@NotNull String url, @NotNull Module module) {
    assert url.equals(URL_EDIT_CLASSPATH) : url;
    ProjectSettingsService.getInstance(module.getProject()).openModuleSettings(module);
  }

  public String createOpenClassUrl(@NotNull String className) {
    return URL_OPEN_CLASS + className;
  }

  private static void handleOpenClassUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_OPEN_CLASS) : url;
    String className = url.substring(URL_OPEN_CLASS.length());
    Project project = module.getProject();
    PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (clz != null) {
      PsiFile containingFile = clz.getContainingFile();
      if (containingFile != null) {
        openEditor(project, containingFile, clz.getTextOffset());
      }
    }
  }

  public String createShowXmlUrl() {
    return URL_SHOW_XML;
  }

  private static void handleShowXmlUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.equals(URL_SHOW_XML) : url;
    openEditor(module.getProject(), file, 0, -1);
  }

  public String createShowTagUrl(String tag) {
    return URL_SHOW_TAG + tag;
  }

  private static void handleShowTagUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_SHOW_TAG) : url;
    final String tagName = url.substring(URL_SHOW_TAG.length());

    XmlTag first = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
      @Override
      @Nullable
      public XmlTag compute() {
        Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
        for (XmlTag tag : xmlTags) {
          if (tagName.equals(tag.getName())) {
            return tag;
          }
        }

        return null;
      }
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first);
    } else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
  }

  public String createNewClassUrl(String className) {
    return URL_CREATE_CLASS + className;
  }

  private static void handleNewClassUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_CREATE_CLASS) : url;
    String s = url.substring(URL_CREATE_CLASS.length());

    final Project project = module.getProject();
    String title = "Create Custom View";

    final String className;
    final String packageName;
    int index = s.lastIndexOf('.');
    if (index == -1) {
      className = s;
      packageName = ManifestInfo.get(module).getPackage();
    } else {
      packageName = s.substring(0, index);
      className = s.substring(index + 1);
    }
    CreateClassDialog dialog = new CreateClassDialog(project, title, className, packageName, CreateClassKind.CLASS, true, module) {
      @Override
      protected boolean reportBaseInSourceSelectionInTest() {
        return true;
      }
    };
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final PsiDirectory targetDirectory = dialog.getTargetDirectory();
      if (targetDirectory != null) {
        PsiClass newClass = ApplicationManager.getApplication().runWriteAction(
          new Computable<PsiClass>() {
            @Override
            public PsiClass compute() {
              PsiClass targetClass = JavaDirectoryService.getInstance().createClass(targetDirectory, className);
              PsiManager manager = PsiManager.getInstance(project);
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
              final PsiElementFactory factory = facade.getElementFactory();

              // Extend android.view.View
              PsiJavaCodeReferenceElement superclassReference =
                factory.createReferenceElementByFQClassName(CLASS_VIEW, targetClass.getResolveScope());
              PsiReferenceList extendsList = targetClass.getExtendsList();
              if (extendsList != null) {
                extendsList.add(superclassReference);
              }

              // Add constructor
              GlobalSearchScope scope = GlobalSearchScope.allScope(project);
              PsiJavaFile javaFile = (PsiJavaFile)targetClass.getContainingFile();
              PsiImportList importList = javaFile.getImportList();
              if (importList != null) {
                PsiClass contextClass = JavaPsiFacade.getInstance(project).findClass("android.content.Context", scope);
                if (contextClass != null) {
                  importList.add(factory.createImportStatement(contextClass));
                }
                PsiClass attributeSetClass = JavaPsiFacade.getInstance(project).findClass("android.util.AttributeSet", scope);
                if (attributeSetClass != null) {
                  importList.add(factory.createImportStatement(attributeSetClass));
                }
              }
              PsiMethod constructor = factory.createMethodFromText(
                  "public " + className + "(Context context, AttributeSet attrs, int defStyle) {\n" +
                  "  super(context, attrs, defStyle);\n" +
                  "}\n", targetClass);
              targetClass.add(constructor);

              // Format class
              CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
              PsiFile containingFile = targetClass.getContainingFile();
              if (containingFile != null) {
                codeStyleManager.reformat(javaFile);
              }

              return targetClass;
            }
          }
        );
        if (newClass != null) {
          PsiFile file = newClass.getContainingFile();
          if (file != null) {
            openEditor(project, file, newClass.getTextOffset());
          }
        }
      }
    }
  }

  public String createOpenStackUrl(@NotNull String className, @NotNull String methodName, @NotNull String fileName, int lineNumber) {
    return URL_OPEN + className + '#' + methodName + ';' + fileName + ':' + lineNumber;
  }

  private static void handleOpenStackUrl(@NotNull String url, @NotNull Module module) {
    assert url.startsWith(URL_OPEN) : url;
    // Syntax: URL_OPEN + className + '#' + methodName + ';' + fileName + ':' + lineNumber;
    int start = URL_OPEN.length();
    int semi = url.indexOf(';', start);
    String className;
    String fileName;
    int line;
    if (semi != -1) {
      className = url.substring(start, semi);
      int colon = url.indexOf(':', semi + 1);
      if (colon != -1) {
        fileName = url.substring(semi + 1, colon);
        line = Integer.decode(url.substring(colon + 1));
      } else {
        fileName = url.substring(semi + 1);
        line = -1;
      }
      // Attempt to open file
    } else {
      className = url.substring(start);
      fileName = null;
      line = -1;
    }
    String method = null;
    int hash = className.indexOf('#');
    if (hash != -1) {
      method = className.substring(hash + 1);
      className = className.substring(0, hash);
    }

    Project project = module.getProject();
    PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (clz != null) {
      PsiFile containingFile = clz.getContainingFile();
      if (fileName != null && containingFile != null && line != -1) {
        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile != null) {
          String name = virtualFile.getName();
          if (fileName.equals(name)) {
            // Use the line number rather than the methodName
            openEditor(project, containingFile, line - 1, -1);
            return;
          }
        }
      }

      if (method != null) {
        PsiMethod[] methodsByName = clz.findMethodsByName(method, true);
        for (PsiMethod m : methodsByName) {
          PsiFile psiFile = m.getContainingFile();
          if (psiFile != null) {
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, m.getTextOffset());
              FileEditorManager.getInstance(project).openEditor(descriptor, true);
              return;
            }
          }
        }
      }

      if (fileName != null) {
        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project));
        for (PsiFile psiFile : files) {
          if (openEditor(project, psiFile, line != -1 ? line - 1 : -1, -1)) {
            break;
          }
        }
      }
    }
  }

  private static boolean openEditor(@NotNull Project project, @NotNull PsiFile psiFile, int line, int column) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, column);
      return !FileEditorManager.getInstance(project).openEditor(descriptor, true).isEmpty();
    }

    return false;
  }

  private static boolean openEditor(@NotNull Project project, @NotNull PsiFile psiFile, int offset) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
      return !FileEditorManager.getInstance(project).openEditor(descriptor, true).isEmpty();
    }

    return false;
  }

  public String createAssignFragmentUrl(@Nullable String id) {
    return URL_ASSIGN_FRAGMENT_URL + (id != null ? id : "");
  }

  private static void handleAssignFragmentUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_ASSIGN_FRAGMENT_URL) : url;

    ChooseClassDialog dialog = new ChooseClassDialog(module, "Fragments", true, CLASS_FRAGMENT, CLASS_V4_FRAGMENT);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }
    final String fragmentClass = dialog.getClassName();

    int start = URL_ASSIGN_FRAGMENT_URL.length();
    final String id;
    if (start == url.length()) {
      // No specific fragment identified; use the first one
      id = null;
    } else {
      id = LintUtils.stripIdPrefix(url.substring(start));
    }

    WriteCommandAction<Void> action = new WriteCommandAction<Void>(module.getProject(), "Assign Fragment", file) {
      @Override
      protected void run(Result<Void> result) throws Throwable {
        for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
          if (!tag.getName().equals(VIEW_FRAGMENT)) {
            continue;
          }

          if (id != null) {
            String tagId = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
            if (tagId == null || !tagId.endsWith(id) || !id.equals(LintUtils.stripIdPrefix(tagId))) {
              continue;
            }
          }

          tag.setAttribute(ATTR_NAME, ANDROID_URI, fragmentClass);
          break;
        }
      }
    };
    action.execute();
  }

  public String createPickLayoutUrl(@NotNull String activityName) {
    return URL_ASSIGN_LAYOUT_URL + activityName;
  }

  public String createAssignLayoutUrl(@NotNull String activityName, @NotNull String layout) {
    return URL_ASSIGN_LAYOUT_URL + activityName + ':' + layout;
  }

  private static void handleAssignLayoutUrl(@NotNull String url, @NotNull final Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_ASSIGN_LAYOUT_URL) : url;
    int start = URL_ASSIGN_LAYOUT_URL.length();
    int layoutStart = url.indexOf(':', start + 1);
    Project project = module.getProject();
    XmlFile xmlFile = (XmlFile)file;
    if (layoutStart == -1) {
      // Only specified activity; pick it
      String activityName = url.substring(start);
      pickLayout(module, xmlFile, activityName);
    } else {
      // Set directory to specified layoutName
      final String activityName = url.substring(start, layoutStart);
      final String layoutName = url.substring(layoutStart + 1);
      final String layout = LAYOUT_RESOURCE_PREFIX + layoutName;
      assignLayout(project, xmlFile, activityName, layout);
    }
  }

  private static void pickLayout(
    @NotNull final Module module,
    @NotNull final XmlFile file,
    @NotNull final String activityName) {

    ChooseResourceDialog dialog = new ChooseResourceDialog(module, new ResourceType[]{ResourceType.LAYOUT}, null, null) {
      @NotNull
      @Override
      protected Action[] createLeftSideActions() {
        return new Action[0];
      }
    };
    dialog.show();

    if (dialog.isOK()) {
      String layout = dialog.getResourceName();
      if (!layout.equals(LAYOUT_RESOURCE_PREFIX + file.getName())) {
        assignLayout(module.getProject(), file, activityName, layout);
      }
    }
  }

  private static void assignLayout(
    @NotNull final Project project,
    @NotNull final XmlFile file,
    @NotNull final String activityName,
    @NotNull final String layout) {

    WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, "Assign Preview Layout", file) {
      @Override
      protected void run(Result<Void> result) throws Throwable {
        SuppressLintIntentionAction.ensureNamespaceImported(getProject(), file, TOOLS_URI);
        Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
        for (XmlTag tag : xmlTags) {
          if (tag.getName().equals(VIEW_FRAGMENT) ) {
            String name = tag.getAttributeValue(ATTR_CLASS);
            if (name == null || name.isEmpty()) {
              name = tag.getAttributeValue(ATTR_NAME, ANDROID_URI);
            }
            if (activityName.equals(name)) {
              tag.setAttribute(ATTR_LAYOUT, TOOLS_URI, layout);
            }
          }
        }
      }
    };
    action.execute();
  }

  public String createIgnoreFragmentsUrl() {
    return URL_ACTION_IGNORE_FRAGMENTS;
  }

  private void handleIgnoreFragments(@NotNull String url, @NotNull RenderResult result) {
    assert url.equals(URL_ACTION_IGNORE_FRAGMENTS);
    RenderLogger.ignoreFragments();
    RenderService renderService = result.getRenderService();
    if (renderService != null) {
      RenderContext renderContext = renderService.getRenderContext();
      if (renderContext != null) {
        renderContext.requestRender();
      }
    }
  }

  public String createEditAttributeUrl(String attribute, String value) {
    return URL_EDIT_ATTRIBUTE + attribute + '/' + value;
  }

  private static void handleEditAttribute(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_EDIT_ATTRIBUTE);
    int attributeStart = URL_EDIT_ATTRIBUTE.length();
    int valueStart = url.indexOf('/');
    final String attributeName = url.substring(attributeStart, valueStart);
    final String value = url.substring(valueStart + 1);

    XmlAttribute first = ApplicationManager.getApplication().runReadAction(new Computable<XmlAttribute>() {
      @Override
      @Nullable
      public XmlAttribute compute() {
        Collection<XmlAttribute> attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute.class);
        for (XmlAttribute attribute : attributes) {
          if (attributeName.equals(attribute.getLocalName()) && value.equals(attribute.getValue())) {
            return attribute;
          }
        }

        return null;
      }
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first.getValueElement());
    } else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
  }

  public String createReplaceAttributeValueUrl(String attribute, String oldValue, String newValue) {
    return URL_REPLACE_ATTRIBUTE_VALUE + attribute + '/' + oldValue + '/' + newValue;
  }

  private static void handleReplaceAttributeValue(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE);
    int attributeStart = URL_REPLACE_ATTRIBUTE_VALUE.length();
    int valueStart = url.indexOf('/');
    int newValueStart = url.indexOf('/', valueStart + 1);
    final String attributeName = url.substring(attributeStart, valueStart);
    final String oldValue = url.substring(valueStart + 1, newValueStart);
    final String newValue = url.substring(newValueStart + 1);

    WriteCommandAction<Void> action = new WriteCommandAction<Void>(module.getProject(), "Set Attribute Value", file) {
      @Override
      protected void run(Result<Void> result) throws Throwable {
        Collection<XmlAttribute> attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute.class);
        int oldValueLen = oldValue.length();
        for (XmlAttribute attribute : attributes) {
          if (attributeName.equals(attribute.getLocalName())) {
            String attributeValue = attribute.getValue();
            if (attributeValue == null) {
              continue;
            }
            if (oldValue.equals(attributeValue)) {
              attribute.setValue(newValue);
            } else {
              int index = attributeValue.indexOf(oldValue);
              if (index != -1) {
                if ((index == 0 || attributeValue.charAt(index - 1) == '|') &&
                    (index + oldValueLen == attributeValue.length() || attributeValue.charAt(index + oldValueLen) == '|')) {
                  attributeValue = attributeValue.substring(0, index) + newValue + attributeValue.substring(index + oldValueLen);
                  attribute.setValue(attributeValue);
                }
              }
            }
          }
        }
      }
    };
    action.execute();
  }
}
