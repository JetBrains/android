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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.npw.assetstudio.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.MaterialDesignIcons;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.lint.checks.SupportAnnotationDetector;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

import static com.android.SdkConstants.*;

/**
 * Implementation of the {@link ViewEditor} abstraction presented
 * to {@link ViewHandler} instances
 */
public class ViewEditorImpl extends ViewEditor {
  private final SceneView mySceneView;

  public ViewEditorImpl(@NotNull SceneView scene) {
    mySceneView = scene;
  }

  @Override
  public int getDpi() {
    return mySceneView.getConfiguration().getDensity().getDpiValue();
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getTargetSdkVersion();
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return mySceneView.getConfiguration();
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return mySceneView.getModel();
  }

  @NotNull
  @Override
  public LayoutlibSceneManager getSceneBuilder() {
    return (LayoutlibSceneManager)mySceneView.getSceneManager();
  }

  @NotNull
  @Override
  public Collection<ViewInfo> getRootViews() {
    RenderResult result = getSceneBuilder().getRenderResult();

    if (result == null) {
      return Collections.emptyList();
    }

    return result.getRootViews();
  }

  @Override
  public boolean moduleContainsResource(@NotNull ResourceType type, @NotNull String name) {
    AndroidFacet facet = mySceneView.getModel().getFacet();
    return ModuleResourceRepository.getOrCreateInstance(facet).hasResourceItem(type, name);
  }

  @Override
  public void copyVectorAssetToMainModuleSourceSet(@NotNull String asset) {
    String path = MaterialDesignIcons.getPathForBasename(asset);

    try (Reader reader = new InputStreamReader(GraphicGenerator.class.getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
      createResourceFile(FD_RES_DRAWABLE, asset + DOT_XML, CharStreams.toString(reader));
    }
    catch (IOException exception) {
      Logger.getInstance(ViewEditorImpl.class).warn(exception);
    }
  }

  @Override
  public void copyLayoutToMainModuleSourceSet(@NotNull String layout, @Language("XML") @NotNull String xml) {
    String message = "Do you want to copy layout " + layout + " to your main module source set?";

    if (Messages.showYesNoDialog(mySceneView.getModel().getProject(), message, "Copy Layout", Messages.getQuestionIcon()) == Messages.NO) {
      return;
    }

    createResourceFile(FD_RES_LAYOUT, layout + DOT_XML, xml);
  }

  private void createResourceFile(@NotNull String resourceDirectory,
                                  @NotNull String resourceFileName,
                                  @NotNull CharSequence resourceFileContent) {
    try {
      VirtualFile directory = getResourceDirectoryChild(resourceDirectory);

      if (directory == null) {
        return;
      }

      Document document = FileDocumentManager.getInstance().getDocument(directory.createChildData(this, resourceFileName));
      assert document != null;

      document.setText(resourceFileContent);
    }
    catch (IOException exception) {
      Logger.getInstance(ViewEditorImpl.class).warn(exception);
    }
  }

  @Nullable
  private VirtualFile getResourceDirectoryChild(@NotNull String child) throws IOException {
    VirtualFile resourceDirectory = mySceneView.getModel().getFacet().getPrimaryResourceDir();

    if (resourceDirectory == null) {
      Logger.getInstance(ViewEditorImpl.class).warn("resourceDirectory is null");
      return null;
    }

    VirtualFile resourceDirectoryChild = resourceDirectory.findChild(child);

    if (resourceDirectoryChild == null) {
      return resourceDirectory.createChildDirectory(this, child);
    }

    return resourceDirectoryChild;
  }

  @Nullable
  @Override
  public Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    Map<NlComponent, Dimension> unweightedSizes = Maps.newHashMap();
    XmlTag parentTag = parent.getTag();
    if (parentTag.isValid()) {
      if (parent.getChildCount() == 0) {
        return Collections.emptyMap();
      }
      Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(parent.getChildCount());
      for (NlComponent child : parent.getChildren()) {
        tagToComponent.put(child.getTag(), child);
      }

      NlModel model = mySceneView.getModel();
      XmlFile xmlFile = model.getFile();
      AndroidFacet facet = model.getFacet();
      RenderService renderService = RenderService.getInstance(facet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(xmlFile, getConfiguration(), logger, null);
      if (task == null) {
        return null;
      }

      // Measure unweighted bounds
      Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, filter);
      task.dispose();
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          ViewInfo viewInfo = entry.getValue();
          viewInfo = RenderService.getSafeBounds(viewInfo);
          Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
          NlComponent child = tagToComponent.get(entry.getKey());
          if (child != null) {
            unweightedSizes.put(child, size);
          }
        }
      }
    }

    return unweightedSizes;
  }

  @Nullable
  @Override
  public String displayResourceInput(@NotNull String title, @NotNull EnumSet<ResourceType> types) {
    NlModel model = mySceneView.getModel();
    ChooseResourceDialog dialog = ChooseResourceDialog.builder()
      .setModule(model.getModule())
      .setTypes(types)
      .setConfiguration(model.getConfiguration())
      .build();

    if (!title.isEmpty()) {
      dialog.setTitle(title);
    }

    dialog.show();

    if (dialog.isOK()) {
      String resource = dialog.getResourceName();

      if (resource != null && !resource.isEmpty()) {
        return resource;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String displayClassInput(@NotNull Set<String> superTypes,
                                  @Nullable final Predicate<PsiClass> filter,
                                  @Nullable String currentValue) {
    Module module = mySceneView.getModel().getModule();
    String[] superTypesArray = ArrayUtil.toStringArray(superTypes);

    Condition<PsiClass> psiFilter = psiClass -> {
      if (isRestricted(psiClass)) {
        // All restriction scopes are currently filtered out
        return false;
      }
      if (filter != null) {
        return filter.test(psiClass);
      }
      return true;
    };

    return ChooseClassDialog.openDialog(module, "Classes", true, psiFilter, superTypesArray);
  }

  public static boolean isRestricted(@NotNull PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) {
      return false;
    }
    for (PsiAnnotation annotation : modifiers.getAnnotations()) {
      if (Objects.equals(annotation.getQualifiedName(), SupportAnnotationDetector.RESTRICT_TO_ANNOTATION)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public SceneView getSceneView() {
    return mySceneView;
  }

  @Override
  public boolean openResource(@NotNull Configuration configuration, @NotNull String reference, @Nullable VirtualFile currentFile) {
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    if (resourceResolver == null) {
      return false;
    }
    ResourceValue resValue = resourceResolver.findResValue(reference, false);
    File path = ResourceHelper.resolveLayout(resourceResolver, resValue);
    if (path != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(path);
      if (file != null) {
        Project project = mySceneView.getModel().getProject();
        if (currentFile != null) {
          return LayoutNavigationManager.getInstance(project).pushFile(currentFile, file);
        }
        else {
          FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true, true);
          if (editors.length > 0) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
