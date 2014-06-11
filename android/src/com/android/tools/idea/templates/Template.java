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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.manifmerger.ICallback;
import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.MergerLog;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.SystemProperties;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.templates.Parameter.Constraint;
import static com.android.tools.idea.templates.TemplateManager.getTemplateRootFolder;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateUtils.readTextFile;

/**
 * Handler which manages instantiating FreeMarker templates, copying resources
 * and merging into existing files
 */
public class Template {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.Template");
  /** Highest supported format; templates with a higher number will be skipped
   * <p>
   * <ul>
   * <li> 1: Initial format, supported by ADT 20 and up.
   * <li> 2: ADT 21 and up. Boolean variables that have a default value and are not
   *    edited by the user would end up as strings in ADT 20; now they are always
   *    proper Booleans. Templates which rely on this should specify format >= 2.
   * <li> 3: The wizard infrastructure passes the {@code isNewProject} boolean variable
   *    to indicate whether a wizard is created as part of a new blank project
   * <li> 4: Constraint type app_package ({@link Constraint#APP_PACKAGE}), provides
   *    srcDir, resDir and manifestDir variables for locations of files
   * </ul>
   */
  static final int CURRENT_FORMAT = 4;

  /**
   * Directory within the template which contains the resources referenced
   * from the template.xml file
   */
  private static final String DATA_ROOT = "root";

  /** Reserved filename which describes each template */
  public static final String TEMPLATE_XML_NAME = "template.xml";

  /** The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories */
  public static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  /** Finds include ':module_name_1', ':module_name_2',... statements in settings.gradle files */
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("(^|\\n)\\s*include +(':[^']+', *)*':[^']+'");

  /** Finds compile '<maven coordinates' in build.gradle files */
  private static final Pattern COMPILE_PATTERN = Pattern.compile("compile[ \\t]*'([^'\\n]+)'");

  private static final String INDENT = "    ";

  /**
   * Most recent thrown exception during template instantiation. This should
   * basically always be null. Used by unit tests to see if any template
   * instantiation recorded a failure.
   */
  @VisibleForTesting
  public static Exception ourMostRecentException;

  // Various tags and attributes used in the template metadata files - template.xml,
  // globals.xml.ftl, recipe.xml.ftl, etc.

  public static final String TAG_MERGE = "merge";
  public static final String TAG_EXECUTE = "execute";
  public static final String TAG_GLOBALS = "globals";
  public static final String TAG_GLOBAL = "global";
  public static final String TAG_PARAMETER = "parameter";
  public static final String TAG_COPY = "copy";
  public static final String TAG_INSTANTIATE = "instantiate";
  public static final String TAG_OPEN = "open";
  public static final String TAG_THUMB = "thumb";
  public static final String TAG_THUMBS = "thumbs";
  public static final String TAG_DEPENDENCY = "dependency";
  public static final String TAG_ICONS = "icons";
  public static final String TAG_MKDIR = "mkdir";
  public static final String ATTR_FORMAT = "format";
  public static final String ATTR_VALUE = "value";
  public static final String ATTR_DEFAULT = "default";
  public static final String ATTR_SUGGEST = "suggest";
  public static final String ATTR_ID = "id";
  public static final String ATTR_NAME = "name";
  public static final String ATTR_DESCRIPTION = "description";
  public static final String ATTR_VERSION = "version";
  public static final String ATTR_MAVEN = "mavenUrl";
  public static final String ATTR_TYPE = "type";
  public static final String ATTR_HELP = "help";
  public static final String ATTR_FILE = "file";
  public static final String ATTR_TO = "to";
  public static final String ATTR_FROM = "from";
  public static final String ATTR_AT = "at";
  public static final String ATTR_CONSTRAINTS = "constraints";
  public static final String ATTR_VISIBILITY = "visibility";
  public static final String ATTR_SOURCE_URL = "href";
  public static final String ATTR_TEMPLATE_MERGE_STRATEGY = "templateMergeStrategy";
  public static final String VALUE_MERGE_STRATEGY_REPLACE = "replace";
  public static final String VALUE_MERGE_STRATEGY_PRESERVE = "preserve";
  public static final String CATEGORY_ACTIVITIES = "activities";
  public static final String CATEGORY_ACTIVITY = "Activity";
  public static final String CATEGORY_PROJECTS = "gradle-projects";
  public static final String CATEGORY_OTHER = "other";
  public static final String CATEGORY_APPLICATION = "Application";

  public static final String BLOCK_DEPENDENCIES = "dependencies";


  /**
   * List of files to open after the wizard has been created (these are
   * identified by {@link #TAG_OPEN} elements in the recipe file
   */
  private final List<File> myFilesToOpen = Lists.newArrayList();

  /** Path to the directory containing the templates */
  private final File myTemplateRoot;

  /* The base directory the template is expanded into */
  private File myOutputRoot;

  /* The directory of the module root for the project being worked with */
  private File myModuleRoot;

  /** The template loader which is responsible for finding (and sharing) template files */
  private final MyTemplateLoader myLoader;

  private TemplateMetadata myMetadata;
  private Project myProject;
  private boolean myNeedsGradleSync;

  /** Creates a new {@link Template} for the given root path */
  @NotNull
  public static Template createFromPath(@NotNull File rootPath) {
    return new Template(rootPath);
  }

  /** Creates a new {@link Template} for the template name, which should
   * be relative to the templates directory */
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

  private Template(@NotNull File rootPath) {
    myTemplateRoot = rootPath;
    myLoader = new MyTemplateLoader(myTemplateRoot.getPath());
  }

  /**
   * Executes the template, rendering it to output files under the given module root directory.
   *
   * @param outputRootPath the filesystem directory that represents the root directory where the template will be expanded.
   * @param moduleRootPath the filesystem directory that represents the root of the IDE project module for the template being expanded.
   * @param args the key/value pairs that are fed into the input parameters for the template.
   */
  public void render(@NotNull File outputRootPath, @NotNull File moduleRootPath, @NotNull Map<String, Object> args) {
    render(outputRootPath, moduleRootPath, args, null);
  }

  /**
   * Executes the template, rendering it to output files under the given module root directory.
   *
   * @param outputRootPath the filesystem directory that represents the root directory where the template will be expanded.
   * @param moduleRootPath the filesystem directory that represents the root of the IDE project module for the template being expanded.
   * @param args the key/value pairs that are fed into the input parameters for the template.
   * @param project the target project of this template.
   */
  public void render(@NotNull File outputRootPath, @NotNull File moduleRootPath, @NotNull Map<String, Object> args,
                     @Nullable Project project) {
    assert outputRootPath.isDirectory() : outputRootPath;

    myFilesToOpen.clear();
    myOutputRoot = outputRootPath;
    myModuleRoot = moduleRootPath;
    myProject = project;

    Map<String, Object> paramMap = createParameterMap(args);
    enforceParameterTypes(getMetadata(), args);
    Configuration freemarker = new Configuration();
    freemarker.setObjectWrapper(new DefaultObjectWrapper());
    freemarker.setTemplateLoader(myLoader);

    processFile(freemarker, new File(TEMPLATE_XML_NAME), paramMap);

    // Handle dependencies
    if (paramMap.containsKey(TemplateMetadata.ATTR_DEPENDENCIES_LIST)) {
      Object maybeDependencyList = paramMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);
      if (maybeDependencyList instanceof List) {
        List<String> dependencyList = (List<String>)maybeDependencyList;
        if (!dependencyList.isEmpty()) {
          try {
            mergeDependenciesIntoFile(freemarker, paramMap, GradleUtil.getGradleBuildFilePath(moduleRootPath));
            myNeedsGradleSync = true;
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
    if (myNeedsGradleSync && myProject != null) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
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

  @NotNull
  public List<File> getFilesToOpen() {
    return myFilesToOpen;
  }

  @NotNull
  public static Map<String, Object> createParameterMap(@NotNull Map<String, Object> args) {
    // Create the data model.
    final Map<String, Object> paramMap = new HashMap<String, Object>();

    // Builtin conversion methods
    paramMap.put("slashedPackageName", new FmSlashedPackageNameMethod());
    paramMap.put("camelCaseToUnderscore", new FmCamelCaseToUnderscoreMethod());
    paramMap.put("underscoreToCamelCase", new FmUnderscoreToCamelCaseMethod());
    paramMap.put("activityToLayout", new FmActivityToLayoutMethod());
    paramMap.put("layoutToActivity", new FmLayoutToActivityMethod());
    paramMap.put("classToResource", new FmClassNameToResourceMethod());
    paramMap.put("escapeXmlAttribute", new FmEscapeXmlAttributeMethod());
    paramMap.put("escapeXmlText", new FmEscapeXmlStringMethod());
    paramMap.put("escapeXmlString", new FmEscapeXmlStringMethod());
    paramMap.put("escapePropertyValue", new FmEscapePropertyValueMethod());
    paramMap.put("extractLetters", new FmExtractLettersMethod());

    // Dependency list
    paramMap.put(TemplateMetadata.ATTR_DEPENDENCIES_LIST, new LinkedList<String>());

    // Root folder of the templates
    if (ApplicationManager.getApplication() != null && getTemplateRootFolder() != null) {
      paramMap.put("templateRoot", getTemplateRootFolder().getAbsolutePath());
    }

    // Wizard parameters supplied by user, specific to this template
    paramMap.putAll(args);

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
      } catch (NumberFormatException e) {
        result = SdkVersionInfo.getApiByPreviewName((String)value, true /* Recognize Unknowns */);
      }
      args.put(key, result);
    }
  }


  /** Read the given FreeMarker file and process the variable definitions */
  private void processFile(@NotNull final Configuration freemarker, @NotNull File file, @NotNull final Map<String, Object> paramMap) {
    try {
      String xml;
      if (hasExtension(file, DOT_XML)) {
        // Just read the file
        xml = readTextFile(getTemplateFile(file));
        if (xml == null) {
          return;
        }
      } else {
        myLoader.setTemplateFile(getTemplateFile(file));
        xml = processFreemarkerTemplate(freemarker, paramMap, file.getName());
      }

      xml = XmlUtils.stripBom(xml);
      InputSource inputSource = new InputSource(new StringReader(xml));
      SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
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
          } else if (TAG_GLOBAL.equals(name)) {
            String id = attributes.getValue(ATTR_ID);
            if (!paramMap.containsKey(id)) {
              paramMap.put(id, TypedVariable.parseGlobal(attributes));
            }
          } else if (TAG_GLOBALS.equals(name)) {
            // Handle evaluation of variables
            File globalsFile = getPath(attributes, ATTR_FILE);
            if (globalsFile != null) {
              processFile(freemarker, globalsFile, paramMap);
            } // else: <globals> root element
          } else if (TAG_EXECUTE.equals(name)) {
            File recipeFile = getPath(attributes, ATTR_FILE);
            if (recipeFile != null) {
              executeRecipeFile(freemarker, recipeFile, paramMap);
            }
          } else if (!name.equals("template") && !name.equals("category") && !name.equals("option") && !name.equals(TAG_THUMBS) &&
                     !name.equals(TAG_THUMB) && !name.equals(TAG_ICONS) && !name.equals(TAG_DEPENDENCY) && !name.equals(TAG_FORMFACTOR)) {
            LOG.error("WARNING: Unknown template directive " + name);
          }
        }
      });
    } catch (Exception e) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  /** Executes the given recipe file: copying, merging, instantiating, opening files etc */
  private void executeRecipeFile(@NotNull final Configuration freemarker, @NotNull File file, @NotNull final Map<String,
    Object> paramMap) {
    try {
      myLoader.setTemplateFile(getTemplateFile(file));
      String xml = processFreemarkerTemplate(freemarker, paramMap, file.getName());

      xml = XmlUtils.stripBom(xml);
      InputSource inputSource = new InputSource(new StringReader(xml));
      SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
          try {
            boolean instantiate = TAG_INSTANTIATE.equals(name);
            if (TAG_COPY.equals(name) || instantiate) {
              File fromFile = getPath(attributes, ATTR_FROM);
              File toFile = getPath(attributes, ATTR_TO);
              if (toFile == null || toFile.getPath().isEmpty()) {
                toFile = getPath(attributes, ATTR_FROM);
                toFile = TemplateUtils.stripSuffix(toFile, DOT_FTL);
              }
              if (instantiate) {
                instantiate(freemarker, paramMap, fromFile, toFile);
              }
              else {
                copyTemplateResource(fromFile, toFile);
              }
            }
            else if (TAG_MERGE.equals(name)) {
              File fromFile = getPath(attributes, ATTR_FROM);
              File toFile = getPath(attributes, ATTR_TO);
              if (toFile == null || toFile.getPath().isEmpty()) {
                toFile = getPath(attributes, ATTR_FROM);
                toFile = TemplateUtils.stripSuffix(toFile, DOT_FTL);
              }
              // Resources in template.xml are located within root/
              merge(freemarker, paramMap, fromFile, toFile);
            }
            else if (name.equals(TAG_OPEN)) {
              // The relative path here is within the output directory:
              File relativePath = getPath(attributes, ATTR_FILE);
              if (relativePath != null && !relativePath.getPath().isEmpty()) {
                myFilesToOpen.add(relativePath);
              }
            }
            else if (name.equals(TAG_MKDIR)) {
              // The relative path here is within the output directory:
              File relativePath = getPath(attributes, ATTR_AT);
              if (relativePath != null && !relativePath.getPath().isEmpty()) {
                File targetFile = getTargetFile(relativePath);
                checkedCreateDirectoryIfMissing(targetFile);
              }
            } else if (name.equals(TAG_DEPENDENCY)) {
              String url = attributes.getValue(ATTR_MAVEN);
              //noinspection unchecked
              List<String> dependencyList = (List<String>)paramMap.get(TemplateMetadata.ATTR_DEPENDENCIES_LIST);

              if (url != null) {
                dependencyList.add(url);
              }
            }
            else if (!name.equals("recipe")) {
              LOG.warn("WARNING: Unknown template directive " + name);
            }
          }
          catch (Exception e) {
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ourMostRecentException = e;
            LOG.warn(e);
          }
        }
      });
    } catch (Exception e) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  private void merge(@NotNull final Configuration freemarker,
                     @NotNull final Map<String, Object> paramMap,
                     @NotNull File relativeFrom,
                     @NotNull File to) throws IOException, TemplateException {

    String targetText = null;

    to = getTargetFile(to);
    if (!(hasExtension(to, DOT_XML) || hasExtension(to, DOT_GRADLE))) {
      throw new RuntimeException("Only XML or Gradle files can be merged at this point: " + to);
    }

    if (to.exists()) {
      targetText = Files.toString(to, Charsets.UTF_8);
    } else if (to.getParentFile() != null) {
      //noinspection ResultOfMethodCallIgnored
      checkedCreateDirectoryIfMissing(to.getParentFile());
    }

    if (targetText == null) {
      // The target file doesn't exist: don't merge, just copy
      boolean instantiate = hasExtension(relativeFrom, DOT_FTL);
      if (instantiate) {
        instantiate(freemarker, paramMap, relativeFrom, to);
      } else {
        copyTemplateResource(relativeFrom, to);
      }
      return;
    }

    String sourceText;
    File from = getFullPath(relativeFrom);
    if (hasExtension(relativeFrom, DOT_FTL)) {
      // Perform template substitution of the template prior to merging
      myLoader.setTemplateFile(from);
      sourceText = processFreemarkerTemplate(freemarker, paramMap, from.getName());
    } else {
      sourceText = readTextFile(from);
      if (sourceText == null) {
        return;
      }
    }

    String contents;
    if (to.getName().equals(GRADLE_PROJECT_SETTINGS_FILE)) {
      contents = mergeGradleSettingsFile(sourceText, targetText);
      myNeedsGradleSync = true;
    } else if (to.getName().equals(SdkConstants.FN_BUILD_GRADLE)) {
      contents = GradleFileMerger.mergeGradleFiles(sourceText, targetText, myProject);
      myNeedsGradleSync = true;
    } else if (hasExtension(to, DOT_XML)) {
      contents = mergeXml(sourceText, targetText, to, paramMap);
    } else {
      throw new RuntimeException("Only XML or Gradle settings files can be merged at this point: " + to);
    }

    writeFile(contents, to);
  }

  private static String mergeXml(String sourceXml, String targetXml, File targetFile, Map<String, Object> paramMap) {
    Document currentDocument = XmlUtils.parseDocumentSilently(targetXml, true);
    assert currentDocument != null : targetXml;
    Document fragment = XmlUtils.parseDocumentSilently(sourceXml, true);
    assert fragment != null : sourceXml;

    XmlFormatStyle formatStyle = XmlFormatStyle.MANIFEST;
    boolean modified;
    boolean ok;
    String fileName = targetFile.getName();
    if (fileName.equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
      modified = ok = mergeManifest(currentDocument, fragment);
    } else {
      // Merge plain XML files
      String parentFolderName = targetFile.getParentFile().getName();
      ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFolderName);
      if (folderType != null) {
        formatStyle = getXmlFormatStyleForFile(targetFile);
      } else {
        formatStyle = XmlFormatStyle.FILE;
      }

      modified = mergeResourceFile(currentDocument, fragment, folderType, paramMap);
      ok = true;
    }

    // Finally write out the merged file (formatting etc)
    String contents = null;
    if (ok) {
      if (modified) {
        contents = XmlPrettyPrinter.prettyPrint(currentDocument, createXmlFormatPreferences(), formatStyle, "\n", targetXml.endsWith("\n"));
      }
    } else {
      // Just insert into file along with comment, using the "standard" conflict
      // syntax that many tools and editors recognize.

      contents = wrapWithMergeConflict(targetXml, sourceXml);
    }
    return contents;
  }

  /**
   * Wraps the given strings in the standard conflict syntax
   * @param original
   * @param added
   * @return
   */
  private static String wrapWithMergeConflict(String original, String added) {
    String sep = "\n";
    return "<<<<<<< Original" + sep
    + original + sep
    + "=======" + sep
    + added
    + ">>>>>>> Added" + sep;
  }

  /** Merges the given resource file contents into the given resource file
   * @param paramMap */
  private static boolean mergeResourceFile(@NotNull Document currentDocument, @NotNull Document fragment,
                                           @Nullable ResourceFolderType folderType, @NotNull Map<String, Object> paramMap) {
    boolean modified = false;

    // Copy namespace declarations
    NamedNodeMap attributes = fragment.getDocumentElement().getAttributes();
    if (attributes != null) {
      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Attr attribute = (Attr)attributes.item(i);
        if (attribute.getName().startsWith(XMLNS_PREFIX)) {
          currentDocument.getDocumentElement().setAttribute(attribute.getName(), attribute.getValue());
        }
      }
    }

    // For layouts for example, I want to *append* inside the root all the
    // contents of the new file.
    // But for resources for example, I want to combine elements which specify
    // the same name or id attribute.
    // For elements like manifest files we need to insert stuff at the right
    // location in a nested way (activities in the application element etc)
    // but that doesn't happen for the other file types.
    Element root = fragment.getDocumentElement();
    NodeList children = root.getChildNodes();
    List<Node> nodes = new ArrayList<Node>(children.getLength());
    for (int i = children.getLength() - 1; i >= 0; i--) {
      Node child = children.item(i);
      nodes.add(child);
      root.removeChild(child);
    }
    Collections.reverse(nodes);

    root = currentDocument.getDocumentElement();

    if (folderType == ResourceFolderType.VALUES) {
      // Try to merge items of the same name
      Map<String, Node> old = new HashMap<String, Node>();
      NodeList newSiblings = root.getChildNodes();
      for (int i = newSiblings.getLength() - 1; i >= 0; i--) {
        Node child = newSiblings.item(i);
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          Element element = (Element) child;
          String name = getResourceId(element);
          if (name != null) {
            old.put(name, element);
          }
        }
      }

      for (Node node : nodes) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          // Chances are, we will put the node from the clean template into the original document, so import it.
          Element element = (Element) currentDocument.importNode(node, true);
          String mergeStrategy = element.getAttribute(ATTR_TEMPLATE_MERGE_STRATEGY);
          // Remove the "templateMergeStrategy" attribute from the final output.
          element.removeAttribute(ATTR_TEMPLATE_MERGE_STRATEGY);

          String name = getResourceId(element);
          Node replace = name != null ? old.get(name) : null;
          if (replace != null) {
            // There is an existing item with the same id. Either replace it
            // or preserve it depending on the "templateMergeStrategy" attribute.
            // If that attribute does not exist, default to preserving it.

            // Let's say you've used the activity wizard once, and it
            // emits some configuration parameter as a resource that
            // it depends on, say "padding". Then the user goes and
            // tweaks the padding to some other number.
            // Now running the wizard a *second* time for some new activity,
            // we should NOT go and set the value back to the template's
            // default!
            if (VALUE_MERGE_STRATEGY_REPLACE.equals(mergeStrategy)) {
              root.replaceChild(element, replace);
              modified = true;
            } else if (VALUE_MERGE_STRATEGY_PRESERVE.equals(mergeStrategy)) {
              // Preserve the existing value.
            } else {
              // No explicit directive given, preserve the original value by default.
              LOG.warn("Warning: Ignoring name conflict in resource file for name " + name);
            }
          } else {
            root.appendChild(element);
            modified = true;
          }
        }
      }
    } else {
      // In other file types, such as layouts, just append all the new content
      // at the end.
      for (Node node : nodes) {
        root.appendChild(currentDocument.importNode(node, true));
        modified = true;
      }
    }
    return modified;
  }

  /** Merges the given manifest fragment into the given manifest file */
  private static boolean mergeManifest(@NotNull Document currentManifest, @NotNull Document fragment) {
    // Transfer package element from manifest to merged in root; required by
    // manifest merger
    Element fragmentRoot = fragment.getDocumentElement();
    Element manifestRoot = currentManifest.getDocumentElement();
    if (fragmentRoot == null || manifestRoot == null) {
      return false;
    }
    String pkg = fragmentRoot.getAttribute(ATTR_PACKAGE);
    if (pkg == null || pkg.isEmpty()) {
      pkg = manifestRoot.getAttribute(ATTR_PACKAGE);
      if (pkg != null && !pkg.isEmpty()) {
        fragmentRoot.setAttribute(ATTR_PACKAGE, pkg);
      }
    }

    ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(new StdLogger(StdLogger.Level.INFO)), new AdtManifestMergeCallback())
      .setExtractPackagePrefix(true);
    return currentManifest != null &&
           fragment != null &&
           merger.process(currentManifest, fragment);
  }

  private static String mergeGradleSettingsFile(@NotNull String source, @NotNull String dest) throws IOException, TemplateException {
    // TODO: Right now this is implemented as a dumb text merge. It would be much better to read it into PSI using IJ's Groovy support.
    // If Gradle build files get first-class PSI support in the future, we will pick that up cheaply. At the moment, Our Gradle-Groovy
    // support requires a project, which we don't necessarily have when instantiating a template.

    StringBuilder contents = new StringBuilder(dest);

    for (String line : Splitter.on('\n').omitEmptyStrings().trimResults().split(source)) {
      if (!line.startsWith("include")) {
        throw new RuntimeException("When merging settings.gradle files, only include directives can be merged.");
      }
      line = line.substring("include".length()).trim();

      Matcher matcher = INCLUDE_PATTERN.matcher(contents);
      if (matcher.find()) {
        contents.insert(matcher.end(), ", " + line);
      } else {
        contents.insert(0, "include " + line + SystemProperties.getLineSeparator());
      }
    }
    return contents.toString();
  }

  /**
   * Merge the given dependency URLs into the given build.gradle file
   * @param paramMap the parameters to merge
   * @param gradleBuildFile the build.gradle file which will be written with the merged dependencies
   */
  private void mergeDependenciesIntoFile(@NotNull final Configuration freemarker, @NotNull Map<String, Object> paramMap,
                                         @NotNull File gradleBuildFile) throws IOException, TemplateException {
    File templateFile = new File(TemplateManager.getTemplateRootFolder().getPath(),
                                 FileUtil.join("gradle", "utils", "dependencies.gradle.ftl"));
    myLoader.setTemplateFile(templateFile);
    String contents = processFreemarkerTemplate(freemarker, paramMap, templateFile.getName());
    String destinationContents;
    if (gradleBuildFile.exists()) {
      destinationContents = TemplateUtils.readTextFile(gradleBuildFile);
    } else {
      destinationContents = "";
    }
    if (destinationContents == null) {
      destinationContents = "";
    }
    String result = GradleFileMerger.mergeGradleFiles(contents, destinationContents, myProject);
    writeFile(result, gradleBuildFile);
  }

  /** Instantiates the given template file into the given output file */
  private void instantiate(
    @NotNull final Configuration freemarker,
    @NotNull final Map<String, Object> paramMap,
    @NotNull File relativeFrom,
    @NotNull File to) throws IOException, TemplateException {
    // For now, treat extension-less files as directories... this isn't quite right
    // so I should refine this! Maybe with a unique attribute in the template file?
    boolean isDirectory = relativeFrom.getName().indexOf('.') == -1;
    if (isDirectory) {
      // It's a directory
      copyTemplateResource(relativeFrom, to);
    } else {
      File from = getFullPath(relativeFrom);
      myLoader.setTemplateFile(from);
      String contents = processFreemarkerTemplate(freemarker, paramMap, from.getName());

      contents = format(contents, to);
      File targetFile = getTargetFile(to);
      VfsUtil.createDirectories(targetFile.getParentFile().getAbsolutePath());
      writeFile(contents, targetFile);
    }
  }

  @NotNull
  private File getFullPath(@NotNull File fromFile) {
    if (fromFile.isAbsolute()) {
      return fromFile;
    } else {
      // If it's a relative file path, get the data from the template data directory
      return new File(myTemplateRoot, DATA_ROOT + File.separator + fromFile);
    }
  }

  @NotNull
  private File getTargetFile(@NotNull File file) throws IOException {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myOutputRoot, file.getPath());
  }

  @NotNull
  private File getTemplateFile(@NotNull File relativeFile) throws IOException {
    return new File(myTemplateRoot, relativeFile.getPath());
  }

  @NotNull
  private static String processFreemarkerTemplate(@NotNull Configuration freemarker,
                                                  @NotNull Map<String, Object> paramMap, @NotNull String name)
    throws IOException, TemplateException {
    freemarker.template.Template inputsTemplate = freemarker.getTemplate(name);
    StringWriter out = new StringWriter();
    inputsTemplate.process(paramMap, out);
    out.flush();
    return out.toString();
  }

  @NotNull
  private static XmlFormatPreferences createXmlFormatPreferences() {
    // TODO: implement
    return XmlFormatPreferences.defaults();
  }

  /**
   * Returns the {@link XmlFormatStyle} to use for resource files of the given path.
   *
   * @param file the file to find the style for
   * @return the suitable format style to use
   */
  @NotNull
  private static XmlFormatStyle getXmlFormatStyleForFile(@NotNull File file) {
    if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      return XmlFormatStyle.MANIFEST;
    }

    if (file.getParent() != null) {
      String parentName = file.getParentFile().getName();
      ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
      return getXmlFormatStyleForFolderType(folderType);
    }

    return XmlFormatStyle.FILE;
  }

  /**
   * Returns the {@link XmlFormatStyle} to use for resource files in the given resource
   * folder
   *
   * @param folderType the type of folder containing the resource file
   * @return the suitable format style to use
   */
  @NotNull
  private static XmlFormatStyle getXmlFormatStyleForFolderType(@NotNull ResourceFolderType folderType) {
    switch (folderType) {
      case LAYOUT:
        return XmlFormatStyle.LAYOUT;
      case COLOR:
      case VALUES:
        return XmlFormatStyle.RESOURCE;
      case ANIM:
      case ANIMATOR:
      case DRAWABLE:
      case INTERPOLATOR:
      case MENU:
      default:
        return XmlFormatStyle.FILE;
    }
  }
  private static String getResourceId(@NotNull Element element) {
    String name = element.getAttribute(ATTR_NAME);
    if (name == null) {
      name = element.getAttribute(ATTR_ID);
    }

    return name;
  }
  private static String format(@NotNull String contents, File to) {
    // TODO: Implement this
    return contents;
  }

  /** Copy a template resource */
  private void copyTemplateResource(
    @NotNull File relativeFrom,
    @NotNull File output) throws IOException {
    copy(getFullPath(relativeFrom), getTargetFile(output));
  }

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  private void copy(@NotNull File src, @NotNull File dest) throws IOException {
    VirtualFile sourceFile = VfsUtil.findFileByIoFile(src, true);
    assert sourceFile != null;
    File parentPath = (src.isDirectory() ? dest : dest.getParentFile());
    VirtualFile destFolder = checkedCreateDirectoryIfMissing(parentPath);
    if (src.isDirectory()) {
      copyDirectory(sourceFile, destFolder);
    }
    else {
      VfsUtilCore.copyFile(this, sourceFile, destFolder);
    }
  }

  /**
   * VfsUtil#copyDirectory messes up the undo stack, most likely by trying to
   * create directory even if it already exists. This is an undo-friendly
   * replacement.
   */
  private void copyDirectory(@NotNull final VirtualFile src, @NotNull final VirtualFile dest) throws IOException {
    final File destinationFile = VfsUtilCore.virtualToIoFile(dest);
    VfsUtilCore.visitChildrenRecursively(src, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        try {
          return copyFile(file, src, destinationFile, dest);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }
    }, IOException.class);
  }

  private boolean copyFile(VirtualFile file, VirtualFile src, File destinationFile, VirtualFile dest) throws IOException {
    String relativePath = VfsUtilCore.getRelativePath(file, src, File.separatorChar);
    if (relativePath == null) {
      LOG.error(file.getPath() + " is not a child of " + src, new Exception());
      return false;
    }
    if (file.isDirectory()) {
      checkedCreateDirectoryIfMissing(new File(destinationFile, relativePath));
    }
    else {
      VirtualFile targetDir = dest;
      if (relativePath.indexOf(File.separatorChar) > 0) {
        String directories = relativePath.substring(0, relativePath.lastIndexOf(File.separatorChar));
        File newParent = new File(destinationFile, directories);
        targetDir = checkedCreateDirectoryIfMissing(newParent);
      }
      VfsUtilCore.copyFile(this, file, targetDir);
    }
    return true;
  }

  /**
   * Creates a directory for the given file and returns the VirtualFile object.
   *
   * @return virtual file object for the given path. It can never be null.
   */
  @NotNull
  public static VirtualFile checkedCreateDirectoryIfMissing(@NotNull File directory) throws IOException {
    VirtualFile dir = VfsUtil.createDirectoryIfMissing(directory.getAbsolutePath());
    if (dir == null) {
      throw new IOException("Unable to create " + directory.getAbsolutePath());
    }
    else {
      return dir;
    }
  }

  /**
   * Replaces the contents of the given file with the given string. Outputs
   * text in UTF-8 character encoding. The file is created if it does not
   * already exist.
   */
  private void writeFile(@Nullable String contents, @NotNull File to) throws IOException {
    if (contents == null) {
      return;
    }
    VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(to);
    if (vf == null) {
      // Creating a new file
      VirtualFile parentDir = checkedCreateDirectoryIfMissing(to.getParentFile());
      vf = parentDir.createChildData(this, to.getName());
    }
    com.intellij.openapi.editor.Document document = FileDocumentManager.getInstance().getDocument(vf);
    if (document != null) {
      document.setText(contents.replaceAll("\r\n", "\n"));
      FileDocumentManager.getInstance().saveDocument(document);
    }
    else {
      vf.setBinaryContent(contents.getBytes(Charsets.UTF_8), -1, -1, this);
    }
  }

  /**
   * Retrieve the named parameter from the attribute list and unescape it from XML as a path
   * @param attributes the map of attributes
   * @param name the name of the attribute to retrieve
   */
  @Nullable
  private static File getPath(@NotNull Attributes attributes, @NotNull String name) {
    String value = attributes.getValue(name);
    if (value == null) {
      return null;
    }
    String unescapedString = XmlUtils.fromXmlAttributeValue(value);
    return new File(FileUtil.toSystemDependentName(unescapedString));
  }

  /**
  * A custom {@link TemplateLoader} which locates and provides templates
  * within the plugin .jar file
  */
  private static final class MyTemplateLoader implements TemplateLoader {
    private String myPrefix;

    public MyTemplateLoader(@Nullable String prefix) {
      myPrefix = prefix;
    }

    public void setTemplateFile(@NotNull File file) {
      setTemplateParent(file.getParentFile());
    }

    public void setTemplateParent(@NotNull File parent) {
      myPrefix = parent.getPath();
    }

    @Override
    @NotNull
    public Reader getReader(@NotNull Object templateSource, @NotNull String encoding) throws IOException {
      URL url = (URL) templateSource;
      return new InputStreamReader(url.openStream(), encoding);
    }

    @Override
    public long getLastModified(Object templateSource) {
      return 0;
    }

    @Override
    @Nullable
    public Object findTemplateSource(@NotNull String name) throws IOException {
      String path = myPrefix != null ? myPrefix + '/' + name : name;
      File file = new File(path);
      if (file.exists()) {
        return SdkUtils.fileToUrl(file);
      }
      return null;
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
    }
  }

  /**
   * A {@link ManifestMerger} {@link ICallback} that returns the
   * proper API level for known API codenames.
   */
  static class AdtManifestMergeCallback implements ICallback {

    @Override
    public int queryCodenameApiLevel(@NotNull String codename) {
      try {
        AndroidVersion version = new AndroidVersion(codename);
        String hashString = AndroidTargetHash.getPlatformHashString(version);
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData != null) {
          IAndroidTarget t = sdkData.getLocalSdk().getTargetFromHashString(hashString);
          if (t != null) {
            return t.getVersion().getApiLevel();
          }
        }
      }
      catch (AndroidVersion.AndroidVersionException ignore) {
      }
      return ICallback.UNKNOWN_CODENAME;
    }
  }

  /**
   * Returns true iff the given file has the given extension (with or without .)
   */
  private static boolean hasExtension(File file, String extension) {
    String noDotExtension = extension.startsWith(".") ? extension.substring(1) : extension;
    return Files.getFileExtension(file.getName()).equalsIgnoreCase(noDotExtension);
  }
}
