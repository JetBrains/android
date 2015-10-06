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
package com.android.tools.idea.templates;

import com.android.annotations.VisibleForTesting;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.recipe.Recipe;
import com.android.tools.idea.templates.recipe.RecipeExecutor;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.utils.XmlUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.templates.FreemarkerUtils.processFreemarkerTemplate;
import static com.android.tools.idea.templates.Parameter.Constraint;
import static com.android.tools.idea.templates.TemplateManager.getTemplateRootFolder;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateUtils.hasExtension;
import static com.android.tools.idea.templates.TemplateUtils.readTextFile;
import static com.android.tools.idea.templates.parse.SaxUtils.getPath;

/**
 * Handler which manages instantiating FreeMarker templates, copying resources
 * and merging into existing files
 */
public class Template {
  /**
   * Reserved filename which describes each template
   */
  public static final String TEMPLATE_XML_NAME = "template.xml";
  // Various tags and attributes used in template.xml
  public static final String TAG_EXECUTE = "execute";
  public static final String TAG_GLOBALS = "globals";
  public static final String TAG_GLOBAL = "global";
  public static final String TAG_PARAMETER = "parameter";
  public static final String TAG_THUMB = "thumb";
  public static final String TAG_THUMBS = "thumbs";
  public static final String TAG_DEPENDENCY = "dependency";
  public static final String TAG_ICONS = "icons";
  public static final String ATTR_FORMAT = "format";
  public static final String ATTR_VALUE = "value";
  public static final String ATTR_DEFAULT = "default";
  public static final String ATTR_SUGGEST = "suggest";
  public static final String ATTR_ID = "id";
  public static final String ATTR_NAME = "name";
  public static final String ATTR_DESCRIPTION = "description";
  public static final String ATTR_TYPE = "type";
  public static final String ATTR_HELP = "help";
  public static final String ATTR_FILE = "file";
  public static final String ATTR_CONSTRAINTS = "constraints";
  public static final String ATTR_VISIBILITY = "visibility";
  public static final String ATTR_ENABLED = "enabled";
  public static final String ATTR_SOURCE_URL = "href";
  public static final String CATEGORY_ACTIVITIES = "activities";
  public static final String CATEGORY_PROJECTS = "gradle-projects";
  public static final String CATEGORY_OTHER = "other";
  public static final String CATEGORY_APPLICATION = "Application";

  /**
   * Highest supported format; templates with a higher number will be skipped
   * <p/>
   * <ul>
   * <li> 1: Initial format, supported by ADT 20 and up.
   * <li> 2: ADT 21 and up. Boolean variables that have a default value and are not
   * edited by the user would end up as strings in ADT 20; now they are always
   * proper Booleans. Templates which rely on this should specify format >= 2.
   * <li> 3: The wizard infrastructure passes the {@code isNewProject} boolean variable
   * to indicate whether a wizard is created as part of a new blank project
   * <li> 4: Constraint type app_package ({@link Constraint#APP_PACKAGE}), provides
   * srcDir, resDir and manifestDir variables for locations of files
   * <li> 5: All files are relative to the template instead of using an implicit "root" folder.
   * </ul>
   */
  static final int CURRENT_FORMAT = 5;

  /**
   * Templates from this version and up use relative (from this template) path names.
   * Recipe files from older versions uses an implicit "root" folder.
   */
  static final int RELATIVE_FILES_FORMAT = 5;

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.Template");
  /**
   * Most recent thrown exception during template instantiation. This should
   * basically always be null. Used by unit tests to see if any template
   * instantiation recorded a failure.
   */
  @VisibleForTesting public static Exception ourMostRecentException;

  /**
   * Path to the directory containing the templates
   */
  private final File myTemplateRoot;

  private TemplateMetadata myMetadata;

  private Template(@NotNull File templateRoot) {
    myTemplateRoot = templateRoot;
  }

  /**
   * Creates a new {@link Template} for the given root path
   */
  @NotNull
  public static Template createFromPath(@NotNull File rootPath) {
    return new Template(rootPath);
  }

  /**
   * Creates a new {@link Template} for the template name, which should
   * be relative to the templates directory
   */
  @NotNull
  public static Template createFromName(@NotNull String category, @NotNull String name) {
    TemplateManager manager = TemplateManager.getInstance();

    // Use the TemplateManager iteration which should merge contents between the
    // extras/templates/ and tools/templates folders and pick the most recent version
    List<File> templates = manager.getTemplates(category);
    for (File file : templates) {
      if (file.getName().equals(name) && category.equals(file.getParentFile().getName())) {
        return new Template(file);
      }
    }

    return new Template(new File(getTemplateRootFolder(), category + File.separator + name));
  }


  @NotNull
  public static Map<String, Object> createParameterMap(@NotNull Map<String, Object> args) {
    final Map<String, Object> paramMap = FreemarkerUtils.createParameterMap(args);

    // Root folder of the templates
    // TODO: This doesn't look like it's used anywhere. Confirm...?
    if (ApplicationManager.getApplication() != null && getTemplateRootFolder() != null) {
      paramMap.put("templateRoot", getTemplateRootFolder().getAbsolutePath());
    }

    return paramMap;
  }

  /**
   * Iterate through parameters and ensure the given map has the correct for each
   * parameter.
   */
  private static void enforceParameterTypes(@NotNull TemplateMetadata metadata, @NotNull Map<String, Object> args) {
    for (Parameter p : metadata.getParameters()) {
      Object o = args.get(p.id);
      if (o == null) {
        continue;
      }
      switch (p.type) {
        case STRING:
          if (!(o instanceof String)) {
            args.put(p.id, o.toString());
          }
          break;
        case BOOLEAN:
          if (!(o instanceof Boolean)) {
            args.put(p.id, Boolean.parseBoolean(o.toString()));
          }
          break;
        case ENUM:
          break;
        case SEPARATOR:
          break;
        case EXTERNAL:
          break;
        case CUSTOM:
          break;
      }
    }
    convertApisToInt(args);
  }

  public static void convertApisToInt(@NotNull Map<String, Object> args) {
    convertToInt(ATTR_BUILD_API, args);
    convertToInt(ATTR_MIN_API_LEVEL, args);
    convertToInt(TemplateMetadata.ATTR_TARGET_API, args);
  }

  private static void convertToInt(@NotNull String key, @NotNull Map<String, Object> args) {
    Object value = args.get(key);
    if (value instanceof String) {
      Integer result;
      try {
        result = Integer.parseInt((String)value);
      }
      catch (NumberFormatException e) {
        result = SdkVersionInfo.getApiByPreviewName((String)value, true /* Recognize Unknowns */);
      }
      args.put(key, result);
    }
  }

  /**
   * Executes the template, rendering it to output files under the directory context.getModuleRoot()
   */
  public void render(@NotNull final RenderingContext context) {
    WriteCommandAction.runWriteCommandAction(context.getProject(), new Runnable() {
      @Override
      public void run() {
        if (context.getProject().isInitialized()) {
          doRender(context);
        }
        else {
          PostprocessReformattingAspect.getInstance(context.getProject()).disablePostprocessFormattingInside(new Runnable() {
            @Override
            public void run() {
              doRender(context);
            }
          });
        }
      }
    });

    String title = myMetadata.getTitle();
    if (title != null) {
      UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_TEMPLATE, UsageTracker.ACTION_TEMPLATE_RENDER, title, null);
    }
  }

  private void doRender(@NotNull RenderingContext context) {
    TemplateMetadata metadata = getMetadata();
    assert metadata != null;

    enforceParameterTypes(metadata, context.getParamMap());

    processFile(context, new File(TEMPLATE_XML_NAME));
  }

  @NotNull
  public File getRootPath() {
    return myTemplateRoot;
  }

  @Nullable
  public TemplateMetadata getMetadata() {
    if (myMetadata == null) {
      myMetadata = TemplateManager.getInstance().getTemplate(myTemplateRoot);
    }

    return myMetadata;
  }

  /**
   * Read the given xml file and, if it uses freemarker syntax (indicated by its file extension),
   * process the variable definitions
   */
  private void processFile(@NotNull final RenderingContext context, @NotNull File file) {
    try {
      String xml;
      if (hasExtension(file, DOT_XML)) {
        // Just read the file
        xml = readTextFile(getTemplateFile(file));
        if (xml == null) {
          return;
        }
        processXml(context, xml);
      }
      else {
        processFreemarkerTemplate(context, file, new FreemarkerUtils.TemplatePostProcessor() {
          @Override
          public void process(@NotNull String xml) throws TemplateProcessingException {
            processXml(context, xml);
          }
        });
      }
    }
    catch (Exception e) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  private void processXml(@NotNull final RenderingContext context, @NotNull String xml) throws TemplateProcessingException {
    try {
      xml = XmlUtils.stripBom(xml);
      InputSource inputSource = new InputSource(new StringReader(xml));
      SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
          Map<String, Object> paramMap = context.getParamMap();
          if (TAG_PARAMETER.equals(name)) {
            String id = attributes.getValue(ATTR_ID);
            if (!paramMap.containsKey(id)) {
              String value = attributes.getValue(ATTR_DEFAULT);
              Object mapValue = value;
              if (value != null && !value.isEmpty()) {
                String type = attributes.getValue(ATTR_TYPE);
                if ("boolean".equals(type)) {
                  mapValue = Boolean.valueOf(value);
                }
              }
              paramMap.put(id, mapValue);
            }
          }
          else if (TAG_GLOBAL.equals(name)) {
            String id = attributes.getValue(ATTR_ID);
            if (!paramMap.containsKey(id)) {
              paramMap.put(id, TypedVariable.parseGlobal(attributes));
            }
          }
          else if (TAG_GLOBALS.equals(name)) {
            // Handle evaluation of variables
            File globalsFile = getPath(attributes, ATTR_FILE);
            if (globalsFile != null) {
              processFile(context, globalsFile);
            } // else: <globals> root element
          }
          else if (TAG_EXECUTE.equals(name)) {
            File recipeFile = getPath(attributes, ATTR_FILE);
            if (recipeFile != null) {
              executeRecipeFile(context, recipeFile);
            }
          }
          else if (!name.equals("template") &&
                   !name.equals("category") &&
                   !name.equals("option") &&
                   !name.equals(TAG_THUMBS) &&
                   !name.equals(TAG_THUMB) &&
                   !name.equals(TAG_ICONS) &&
                   !name.equals(TAG_DEPENDENCY) &&
                   !name.equals(TAG_FORMFACTOR)) {
            LOG.error("WARNING: Unknown template directive " + name);
          }
        }
      });
    }
    catch (SAXException ex) {
      throw new TemplateProcessingException(ex);
    }
    catch (ParserConfigurationException ex) {
      throw new TemplateProcessingException(ex);
    }
    catch (IOException ex) {
      throw new TemplateProcessingException(ex);
    }
  }

  /**
   * Executes the given recipe file: copying, merging, instantiating, opening files etc
   */
  private void executeRecipeFile(@NotNull final RenderingContext context, @NotNull File fileRecipe) {
    try {
      processFreemarkerTemplate(context, fileRecipe, new FreemarkerUtils.TemplatePostProcessor() {
        @Override
        public void process(@NotNull String xml) throws TemplateProcessingException {
          try {
            xml = XmlUtils.stripBom(xml);

            Recipe recipe = Recipe.parse(new StringReader(xml));
            RecipeExecutor recipeExecutor = context.getRecipeExecutor();
            TemplateMetadata metadata = getMetadata();
            assert metadata != null;
            if (metadata.useImplicitRootFolder()) {
              context.getLoader().setTemplateFolder(new File(context.getLoader().getTemplateFolder(), "root"));
            }
            recipe.execute(recipeExecutor);
          }
          catch (JAXBException ex) {
            throw new TemplateProcessingException(ex);
          }
        }
      });
    }
    catch (Exception e) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  @NotNull
  private File getTemplateFile(@NotNull File relativeFile) throws IOException {
    return new File(myTemplateRoot, relativeFile.getPath());
  }
}
