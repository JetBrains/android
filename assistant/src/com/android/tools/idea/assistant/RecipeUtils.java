/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.templates.FreemarkerUtils;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.Recipe;
import com.android.tools.idea.templates.recipe.RecipeExecutor;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.google.common.collect.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of utility methods for interacting with an {@code Recipe}.
 */
public class RecipeUtils {

  private static Map<Pair<Recipe, Project>, List<RecipeMetadata>> myRecipeMetadataCache = new HashMap<>();

  @NotNull
  public static RecipeMetadata getRecipeMetadata(@NotNull Recipe recipe, @NotNull Module module) {
    Pair key = new Pair(recipe, module.getProject());
    if (myRecipeMetadataCache.containsKey(key)) {
      List<RecipeMetadata> metadataSet = myRecipeMetadataCache.get(key);
      for (RecipeMetadata metadata : metadataSet) {
        if (metadata.getRecipe().equals(recipe)) {
          return metadata;
        }
      }
    }

    SetMultimap<String, String> dependencies = LinkedHashMultimap.create();
    Set<String> classpathEntries = Sets.newHashSet();
    Set<String> plugins = Sets.newHashSet();
    List<File> sourceFiles = Lists.newArrayList();
    List<File> targetFiles = Lists.newArrayList();
    RecipeMetadata metadata = new RecipeMetadata(recipe, module);
    RenderingContext context = null;

    try {
      File moduleRoot = new File(module.getModuleFilePath()).getParentFile();
      // TODO: do we care about this path?
      File RootPath = new File(FileUtil.generateRandomTemporaryPath(), "unused");
      RootPath.deleteOnExit();
      context = RenderingContext.Builder
        .newContext(RootPath, module.getProject())
        .withOutputRoot(moduleRoot)
        .withModuleRoot(moduleRoot)
        .withFindOnlyReferences(true)
        .withPerformSync(false)
        .intoDependencies(dependencies)
        .intoClasspathEntries(classpathEntries)
        .intoPlugins(plugins)
        .intoSourceFiles(sourceFiles)
        .intoTargetFiles(targetFiles)
        .build();
      RecipeExecutor recipeExecutor = context.getRecipeExecutor();
      recipe.execute(recipeExecutor);
    }
    catch (FreemarkerUtils.TemplateProcessingException e) {
      // TODO(b/31039466): Extra logging to track down a rare issue.
      getLog().warn("Template processing exception with context in the following state: " + context);
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Ignore test configurations here.
    for (String d : dependencies.get(SdkConstants.GRADLE_COMPILE_CONFIGURATION)) {
      metadata.addDependency(d);
    }
    for (String c : classpathEntries) {
      metadata.addClasspathEntry(c);
    }
    for (String p : plugins) {
      metadata.addPlugin(p);
    }
    for (File f : sourceFiles) {
      if (f.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
        parseManifestForPermissions(f, metadata);
      }
    }
    for (File f : targetFiles) {
      metadata.addModifiedFile(f);
    }
    return metadata;
  }

  @NotNull
  public static List<RecipeMetadata> getRecipeMetadata(@NotNull Recipe recipe, @NotNull Project project) {
    Pair key = new Pair(recipe, project);
    if (!myRecipeMetadataCache.containsKey(key)) {
      ImmutableList.Builder<RecipeMetadata> cache = ImmutableList.builder();
      for (Module module : GradleProjectInfo.getInstance(project).getAndroidModules()) {
        cache.add(getRecipeMetadata(recipe, module));
      }
      myRecipeMetadataCache.put(key, cache.build());
    }
    return myRecipeMetadataCache.get(key);
  }

  public static void execute(@NotNull Recipe recipe, @NotNull Module module) {
    List<File> filesToOpen = Lists.newArrayList();
    File moduleRoot = new File(module.getModuleFilePath()).getParentFile();
    File RootPath = null;
    try {
      RootPath = new File(FileUtil.generateRandomTemporaryPath(), "unused");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    RenderingContext context = RenderingContext.Builder
      .newContext(RootPath, module.getProject())
      .withOutputRoot(moduleRoot)
      .withModuleRoot(moduleRoot)
      .intoOpenFiles(filesToOpen)
      .build();

    RecipeExecutor recipeExecutor = context.getRecipeExecutor();

    new WriteCommandAction.Simple(module.getProject(), "Executing recipe instructions") {
      @Override
      protected void run() throws Throwable {
        recipe.execute(recipeExecutor);
      }
    }.execute();

    TemplateUtils.openEditors(module.getProject(), filesToOpen, true);
  }

  private static void parseManifestForPermissions(@NotNull File f, @NotNull RecipeMetadata metadata) {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(f, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String tagName, Attributes attributes) throws SAXException {
          if (tagName.equals(SdkConstants.TAG_USES_PERMISSION) ||
              tagName.equals(SdkConstants.TAG_USES_PERMISSION_SDK_23) ||
              tagName.equals(SdkConstants.TAG_USES_PERMISSION_SDK_M)) {
            String permission = attributes.getValue(SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_NAME);
            // Most permissions are "android.permission.XXX", so for readability, just remove the prefix if present
            permission = permission.replace(SdkConstants.ANDROID_PKG_PREFIX + SdkConstants.ATTR_PERMISSION + ".", "");
            metadata.addPermission(permission);
          }
        }
      });
    }
    catch (Exception e) {
      // This method shouldn't crash the user for any reason, as showing permissions is just
      // informational, but log a warning so developers can see if they make a mistake when
      // creating their service.
      getLog().warn("Failed to read permissions from AndroidManifest.xml", e);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(RecipeUtils.class);
  }

}
