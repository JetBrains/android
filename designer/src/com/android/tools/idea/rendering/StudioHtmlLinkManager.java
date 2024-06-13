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

import static com.android.AndroidXConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.CLASS_ATTRIBUTE_SET;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.support.FragmentTagUtil.isFragmentTag;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_ACTION_IGNORE_FRAGMENTS;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_ADD_DEBUG_DEPENDENCY;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_ADD_DEPENDENCY;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_ASSIGN_FRAGMENT_URL;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_ASSIGN_LAYOUT_URL;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_BUILD;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_BUILD_MODULE;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_CLEAR_CACHE_AND_NOTIFY;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_CREATE_CLASS;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_DISABLE_SANDBOX;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_EDIT_ATTRIBUTE;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_EDIT_CLASSPATH;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_OPEN;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_OPEN_CLASS;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_REFRESH_RENDER;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_REPLACE_ATTRIBUTE_VALUE;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_REPLACE_TAGS;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_SHOW_TAG;
import static com.android.tools.rendering.HtmlLinkManagerKt.URL_SYNC;

import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.resources.ResourceType;
import com.android.tools.idea.projectsystem.DependencyType;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.rendering.HtmlLinkManager;
import com.android.tools.rendering.RenderLogger;
import com.android.tools.rendering.security.RenderSecurityManager;
import com.android.utils.SdkUtils;
import com.android.utils.SdkUtils.FileLineColumnUrlData;
import com.android.utils.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.PsiNavigateUtil;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils;
import org.jetbrains.kotlin.resolve.jvm.JvmClassName;

public class StudioHtmlLinkManager implements HtmlLinkManager {
  private static final String URL_SHOW_XML = "action:showXml";
  private static final String URL_RUNNABLE = "runnable:";

  private SparseArray<Action> myLinkRunnables;
  private int myNextLinkId = 0;

  public StudioHtmlLinkManager() {
  }

  /**
   * {@link NotificationGroup} used to let the user now that the click on a link did something. This is meant to be used
   * in those actions that do not trigger any UI updates (like Copy stack trace to clipboard).
   */
  private static final NotificationGroup NOTIFICATIONS_GROUP = new NotificationGroup(
    "Render error panel notifications", NotificationDisplayType.BALLOON, false, null, null, null, PluginId.getId("org.jetbrains.android"));

  @Override
  public void showNotification(@NotNull String content) {
    Notification notification = NOTIFICATIONS_GROUP.createNotification(content, NotificationType.INFORMATION);
    Notifications.Bus.notify(notification);

    new Alarm().addRequest(
      notification::expire,
      TimeUnit.SECONDS.toMillis(2)
    );
  }

  @Override
  public void handleUrl(@NotNull String url, @Nullable Module module, @Nullable PsiFile file,
                        boolean hasRenderResult, @NotNull HtmlLinkManager.RefreshableSurface surface) {
    if (url.startsWith("http:") || url.startsWith("https:")) {
      BrowserLauncher.getInstance().browse(url, null, module == null ? null : module.getProject());
    }
    else if (url.startsWith("file:")) {
      assert module != null;
      handleFileUrl(url, module);
    }
    else if (url.startsWith(URL_REPLACE_TAGS)) {
      assert module != null;
      assert file != null;
      handleReplaceTagsUrl(url, module, file);
    }
    else if (url.equals(URL_BUILD_MODULE)) {
      assert module != null;
      handleBuildModuleUrl(url, file);
    }
    else if (url.equals(URL_BUILD)) {
      assert module != null;
      handleBuildProjectUrl(url, module.getProject());
    }
    else if (url.equals(URL_SYNC)) {
      assert module != null;
      handleSyncProjectUrl(url, module.getProject());
    }
    else if (url.equals(URL_EDIT_CLASSPATH)) {
      assert module != null;
      handleEditClassPathUrl(url, module);
    }
    else if (url.startsWith(URL_CREATE_CLASS)) {
      assert module != null && file != null;
      handleNewClassUrl(url, module);
    }
    else if (url.startsWith(URL_OPEN)) {
      assert module != null;
      handleOpenStackUrl(url, module);
    }
    else if (url.startsWith(URL_OPEN_CLASS)) {
      assert module != null;
      handleOpenClassUrl(url, module);
    }
    else if (url.equals(URL_SHOW_XML)) {
      assert module != null && file != null;
      handleShowXmlUrl(url, module, file);
    }
    else if (url.startsWith(URL_SHOW_TAG)) {
      assert module != null && file != null;
      handleShowTagUrl(url, module, file);
    }
    else if (url.startsWith(URL_ASSIGN_FRAGMENT_URL)) {
      assert module != null && file != null;
      handleAssignFragmentUrl(url, module, file);
    }
    else if (url.startsWith(URL_ASSIGN_LAYOUT_URL)) {
      assert module != null && file != null;
      handleAssignLayoutUrl(url, module, file);
    }
    else if (url.equals(URL_ACTION_IGNORE_FRAGMENTS)) {
      assert hasRenderResult;
      handleIgnoreFragments(url, surface);
    }
    else if (url.startsWith(URL_EDIT_ATTRIBUTE)) {
      assert hasRenderResult;
      if (module != null && file != null) {
        handleEditAttribute(url, module, file);
      }
    }
    else if (url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE)) {
      assert hasRenderResult;
      if (module != null && file != null) {
        handleReplaceAttributeValue(url, module, file);
      }
    }
    else if (url.startsWith(URL_DISABLE_SANDBOX)) {
      assert module != null;
      handleDisableSandboxUrl(module, surface);
    }
    else if (url.startsWith(URL_RUNNABLE)) {
      Action linkRunnable = getLinkRunnable(url);
      if (linkRunnable != null) {
        linkRunnable.actionPerformed(module);
      }
    }
    else if ((url.startsWith(URL_ADD_DEPENDENCY) || url.startsWith(URL_ADD_DEBUG_DEPENDENCY)) && module != null) {
      handleAddDependency(url, module);
      ProjectSystemUtil.getSyncManager(module.getProject())
        .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
    }
    else if (url.startsWith(URL_REFRESH_RENDER)) {
      surface.handleRefreshRenderUrl();
    }
    else if (url.startsWith(URL_CLEAR_CACHE_AND_NOTIFY)) {
      // This does the same as URL_REFRESH_RENDERER with the only difference of displaying a notification afterwards. The reason to have
      // handler is that we have different entry points for the action, one of which is "Clear cache". The user probably expects a result
      // of clicking that link that has something to do with the cache being cleared.
      surface.handleRefreshRenderUrl();
      showNotification("Cache cleared");
    }
    else {
      assert false : "Unexpected URL: " + url;
    }
  }

  private static void handleFileUrl(@NotNull String url, @NotNull Module module) {
    Project project = module.getProject();
    FileLineColumnUrlData parsed = SdkUtils.parseDecoratedFileUrlString(url);
    int line = parsed.line == null ? -1 : parsed.line;
    int column = parsed.column == null ? 0 : parsed.column;
    try {
      File ioFile = SdkUtils.urlToFile(parsed.urlString);
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
      if (file != null) {
        openEditor(project, file, line, column);
      }
    }
    catch (MalformedURLException e) {
      // Ignore
    }
  }

  static abstract class StudioCommandLink implements CommandLink {
    private final String myCommandName;
    private final PsiFile myFile;

    StudioCommandLink(@NotNull String commandName, @NotNull PsiFile file) {
      myCommandName = commandName;
      myFile = file;
    }

    @Override
    public void executeCommand() {
      WriteCommandAction.writeCommandAction(myFile.getProject(), myFile).withName(myCommandName).run(() -> run());
    }
  }

  @NotNull
  @Override
  public String createCommandLink(@NotNull CommandLink command) {
    return createActionLink(module -> command.run());
  }

  @NotNull
  @Override
  public String createActionLink(@NotNull Action action) {
    String url = URL_RUNNABLE + myNextLinkId;
    if (myLinkRunnables == null) {
      myLinkRunnables = new SparseArray<>(5);
    }
    myLinkRunnables.put(myNextLinkId, action);
    myNextLinkId++;

    return url;
  }

  @Nullable
  private Action getLinkRunnable(String url) {
    if (myLinkRunnables != null && url.startsWith(URL_RUNNABLE)) {
      String idString = url.substring(URL_RUNNABLE.length());
      int id = Integer.decode(idString);
      return myLinkRunnables.get(id);
    }
    return null;
  }

  private static void handleReplaceTagsUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.startsWith(URL_REPLACE_TAGS) : url;
    int start = URL_REPLACE_TAGS.length();
    int delimiterPos = url.indexOf('/', start);
    if (delimiterPos != -1) {
      String wrongTag = url.substring(start, delimiterPos);
      String rightTag = url.substring(delimiterPos + 1);
      new ReplaceTagFix((XmlFile)file, wrongTag, rightTag).run();
    }
  }

  private static void handleBuildModuleUrl(@NotNull String url, @NotNull PsiFile psiFile) {
    assert url.equals(URL_BUILD_MODULE) : url;
    ProjectSystemUtil.getProjectSystem(psiFile.getProject()).getBuildManager().compileFilesAndDependencies(Lists.newArrayList(psiFile.getVirtualFile()));
  }

  private static void handleBuildProjectUrl(@NotNull String url, @NotNull Project project) {
    assert url.equals(URL_BUILD) : url;
    ProjectSystemUtil.getProjectSystem(project).getBuildManager().compileProject();
  }

  private static void handleSyncProjectUrl(@NotNull String url, @NotNull Project project) {
    assert url.equals(URL_SYNC) : url;

    ProjectSystemSyncManager.SyncReason reason = project.isInitialized() ? ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED : ProjectSystemSyncManager.SyncReason.PROJECT_LOADED;
    ProjectSystemUtil.getProjectSystem(project).getSyncManager().syncProject(reason);
  }

  private static void handleEditClassPathUrl(@NotNull String url, @NotNull Module module) {
    assert url.equals(URL_EDIT_CLASSPATH) : url;
    ProjectSettingsService.getInstance(module.getProject()).openModuleSettings(module);
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

  private static void handleShowXmlUrl(@NotNull String url, @NotNull Module module, @NotNull PsiFile file) {
    assert url.equals(URL_SHOW_XML) : url;
    openEditor(module.getProject(), file, 0, -1);
  }

  private static void handleShowTagUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_SHOW_TAG) : url;
    final String tagName = url.substring(URL_SHOW_TAG.length());

    XmlTag first = ApplicationManager.getApplication().runReadAction((Computable<XmlTag>)() -> {
      Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
      for (XmlTag tag : xmlTags) {
        if (tagName.equals(tag.getName())) {
          return tag;
        }
      }

      return null;
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first);
    }
    else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
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
      packageName = ProjectSystemUtil.getModuleSystem(module).getPackageName();
      if (packageName == null) {
        return;
      }
    }
    else {
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
        PsiClass newClass = WriteCommandAction.writeCommandAction(project).withName("Create Class").compute(()-> {
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
              PsiClass contextClass = JavaPsiFacade.getInstance(project).findClass(CLASS_CONTEXT, scope);
              if (contextClass != null) {
                importList.add(factory.createImportStatement(contextClass));
              }
              PsiClass attributeSetClass = JavaPsiFacade.getInstance(project).findClass(CLASS_ATTRIBUTE_SET, scope);
              if (attributeSetClass != null) {
                importList.add(factory.createImportStatement(attributeSetClass));
              }
            }

            PsiMethod constructor1arg = factory.createMethodFromText(
              "public " + className + "(Context context) {\n" +
              "  this(context, null);\n" +
              "}\n", targetClass);
            targetClass.add(constructor1arg);

            PsiMethod constructor2args = factory.createMethodFromText(
              "public " + className + "(Context context, AttributeSet attrs) {\n" +
              "  this(context, attrs, 0);\n" +
              "}\n", targetClass);
            targetClass.add(constructor2args);

            PsiMethod constructor3args = factory.createMethodFromText(
              "public " + className + "(Context context, AttributeSet attrs, int defStyle) {\n" +
              "  super(context, attrs, defStyle);\n" +
              "}\n", targetClass);
            targetClass.add(constructor3args);

            // Format class
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
            PsiFile containingFile = targetClass.getContainingFile();
            if (containingFile != null) {
              codeStyleManager.reformat(javaFile);
            }

            return targetClass;
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

  @VisibleForTesting
  static void handleOpenStackUrl(@NotNull String url, @NotNull Module module) {
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
      }
      else {
        fileName = url.substring(semi + 1);
        line = -1;
      }
      // Attempt to open file
    }
    else {
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
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(project);

    // First, try to find a source file corresponding to the class, because other search mechanisms below might return the .class files.
    if (fileName != null) {
      PsiFile containingFile = DebuggerUtils.INSTANCE.findSourceFileForClassIncludeLibrarySources(
        project,
        searchScope,
        JvmClassName.byInternalName(className.replace(".", "/")),
        fileName
      );
      if (containingFile != null) {
        openEditor(project, containingFile, line - 1, -1);
        return;
      }
    }

    PsiClass clz = JavaPsiFacade.getInstance(project).findClass(className, searchScope);
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
        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, searchScope);
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
      return openEditor(project, file, line, column);
    }

    return false;
  }

  private static boolean openEditor(@NotNull Project project, @NotNull VirtualFile file, int line, int column) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, column);
    FileEditorManager manager = FileEditorManager.getInstance(project);

    // Attempt to prefer text editor if it's available for this file
    if (manager.openTextEditor(descriptor, true) != null) {
      return true;
    }

    return !manager.openEditor(descriptor, true).isEmpty();
  }

  private static boolean openEditor(@NotNull Project project, @NotNull PsiFile psiFile, int offset) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      return openEditor(project, file, offset);
    }

    return false;
  }

  private static boolean openEditor(@NotNull Project project, @NotNull VirtualFile file, int offset) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
    return !FileEditorManager.getInstance(project).openEditor(descriptor, true).isEmpty();
  }

  /**
   * Converts a (possibly ambiguous) class name like A.B.C.D into an Android-style class name
   * like A.B$C$D where package names are separated by dots and inner classes are separated by dollar
   * signs (similar to how the internal JVM format uses dollar signs for inner classes but slashes
   * as package separators)
   */
  @NotNull
  private static String getFragmentClass(@NotNull final Module module, @NotNull final String fqcn) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      Project project = module.getProject();
      JavaPsiFacade finder = JavaPsiFacade.getInstance(project);
      PsiClass psiClass = finder.findClass(fqcn, module.getModuleScope());
      if (psiClass == null) {
        psiClass = finder.findClass(fqcn, GlobalSearchScope.allScope(project));
      }

      if (psiClass != null) {
        String jvmClassName = ClassUtil.getJVMClassName(psiClass);
        if (jvmClassName != null) {
          return jvmClassName.replace('/', '.');
        }
      }

      return fqcn;
    });
  }

  private static void handleAssignFragmentUrl(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_ASSIGN_FRAGMENT_URL) : url;

    Predicate<PsiClass> psiFilter = ChooseClassDialog.getUserDefinedPublicAndUnrestrictedFilter();
    String className = ChooseClassDialog
      .openDialog(module, "Fragments", null, psiFilter, CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName(), CLASS_V4_FRAGMENT.newName());
    if (className == null) {
      return;
    }
    final String fragmentClass = getFragmentClass(module, className);

    int start = URL_ASSIGN_FRAGMENT_URL.length();
    final String id;
    if (start == url.length()) {
      // No specific fragment identified; use the first one
      id = null;
    }
    else {
      id = Lint.stripIdPrefix(url.substring(start));
    }

    WriteCommandAction.writeCommandAction(module.getProject(), file).withName("Assign Fragment").run(()-> {
        Collection<XmlTag> tags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
        for (XmlTag tag : tags) {
          if (!isFragmentTag(tag.getName())) {
            continue;
          }

          if (id != null) {
            String tagId = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
            if (tagId == null || !tagId.endsWith(id) || !id.equals(Lint.stripIdPrefix(tagId))) {
              continue;
            }
          }

          if (tag.getAttribute(ATTR_NAME, ANDROID_URI) == null && tag.getAttribute(ATTR_CLASS) == null) {
            tag.setAttribute(ATTR_NAME, ANDROID_URI, fragmentClass);
            return;
          }
        }

        if (id == null) {
          for (XmlTag tag : tags) {
            if (!isFragmentTag(tag.getName())) {
              continue;
            }

            tag.setAttribute(ATTR_NAME, ANDROID_URI, fragmentClass);
            break;
          }
        }
      });
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
    }
    else {
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

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    ResourcePickerDialog dialog = ResourceChooserHelperKt.createResourcePickerDialog(
      "Choose a Layout",
      null,
      facet,
      EnumSet.of(ResourceType.LAYOUT),
      null,
      true,
      false,
      true,
      file.getVirtualFile()
    );

    if (dialog.showAndGet()) {
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

    WriteCommandAction.writeCommandAction(project, file).withName("Assign Preview Layout").run(() -> {
      IdeResourcesUtil.ensureNamespaceImported(file, TOOLS_URI, null);
      Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
      for (XmlTag tag : xmlTags) {
        if (isFragmentTag(tag.getName())) {
          String name = tag.getAttributeValue(ATTR_CLASS);
          if (name == null || name.isEmpty()) {
            name = tag.getAttributeValue(ATTR_NAME, ANDROID_URI);
          }
          if (activityName.equals(name)) {
            tag.setAttribute(ATTR_LAYOUT, TOOLS_URI, layout);
          }
        }
      }
    });
  }

  private static void handleIgnoreFragments(@NotNull String url, @NotNull HtmlLinkManager.RefreshableSurface surface) {
    assert url.equals(URL_ACTION_IGNORE_FRAGMENTS);
    RenderLogger.ignoreFragments();
    surface.requestRender();
  }

  private static void handleEditAttribute(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_EDIT_ATTRIBUTE);
    int attributeStart = URL_EDIT_ATTRIBUTE.length();
    int valueStart = url.indexOf('/');
    final String attributeName = url.substring(attributeStart, valueStart);
    final String value = url.substring(valueStart + 1);

    XmlAttribute first = ApplicationManager.getApplication().runReadAction((Computable<XmlAttribute>)() -> {
      Collection<XmlAttribute> attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute.class);
      for (XmlAttribute attribute : attributes) {
        if (attributeName.equals(attribute.getLocalName()) && value.equals(attribute.getValue())) {
          return attribute;
        }
      }

      return null;
    });

    if (first != null) {
      PsiNavigateUtil.navigate(first.getValueElement());
    }
    else {
      // Fall back to just opening the editor
      openEditor(module.getProject(), file, 0, -1);
    }
  }

  private static void handleReplaceAttributeValue(@NotNull String url, @NotNull Module module, @NotNull final PsiFile file) {
    assert url.startsWith(URL_REPLACE_ATTRIBUTE_VALUE);
    int attributeStart = URL_REPLACE_ATTRIBUTE_VALUE.length();
    int valueStart = url.indexOf('/');
    int newValueStart = url.indexOf('/', valueStart + 1);
    final String attributeName = url.substring(attributeStart, valueStart);
    final String oldValue = url.substring(valueStart + 1, newValueStart);
    final String newValue = url.substring(newValueStart + 1);

    WriteCommandAction.writeCommandAction(module.getProject(), file).withName("Set Attribute Value").run(() -> {
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
          }
          else {
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
    });
  }

  private static void handleDisableSandboxUrl(@NotNull Module module, @Nullable HtmlLinkManager.RefreshableSurface surface) {
    RenderSecurityManager.sEnabled = false;
    surface.requestRender();

    Messages.showInfoMessage(module.getProject(),
                             "The custom view rendering sandbox was disabled for this session.\n\n" +
                             "You can turn it off permanently by adding\n" +
                             RenderSecurityManager.ENABLED_PROPERTY + "=" + VALUE_FALSE + "\n" +
                             "to {install}/bin/idea.properties.",
                             "Disabled Rendering Sandbox");
  }

  @VisibleForTesting
  @UiThread
  static void handleAddDependency(@NotNull String url, @NotNull final Module module) {
    String coordinateStr;
    DependencyType dependencyType;
    if (url.startsWith(URL_ADD_DEPENDENCY)) {
      dependencyType = DependencyType.IMPLEMENTATION;
      coordinateStr = url.substring(URL_ADD_DEPENDENCY.length());
    }
    else if (url.startsWith(URL_ADD_DEBUG_DEPENDENCY)) {
      dependencyType = DependencyType.DEBUG_IMPLEMENTATION;
      coordinateStr = url.substring(URL_ADD_DEBUG_DEPENDENCY.length());
    }
    else {
      return;
    }

    GoogleMavenArtifactId id = GoogleMavenArtifactId.find(coordinateStr);
    if (id == null) {
      Logger.getInstance(StudioHtmlLinkManager.class).warn("Invalid coordinate " + coordinateStr);
      return;
    }
    if (DependencyManagementUtil.addDependenciesWithUiConfirmation(module, Collections.singletonList(id.getCoordinate("+")), false,
                                                                   false, dependencyType)
      .isEmpty()) {
      return;
    }
    Logger.getInstance(StudioHtmlLinkManager.class).warn("Could not add dependency " + id);
  }
}
