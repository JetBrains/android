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
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;
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
import static com.android.tools.idea.templates.TemplateManager.getTemplateRootFolder;

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
   * </ul>
   */
  static final int CURRENT_FORMAT = 3;

  /**
   * Special marker indicating that this path refers to the special shared
   * resource directory rather than being somewhere inside the root/ directory
   * where all template specific resources are found
   */
  private static final String VALUE_TEMPLATE_DIR = "$TEMPLATEDIR";

  /**
   * Directory within the template which contains the resources referenced
   * from the template.xml file
   */
  private static final String DATA_ROOT = "root";

  /**
   * Shared resource directory containing common resources shared among
   * multiple templates
   */
  private static final String RESOURCE_ROOT = "resources";

  /** Reserved filename which describes each template */
  static final String TEMPLATE_XML = "template.xml";

  /** The settings.gradle lives at project root and points gradle at the build files for individual modules in their subdirectories */
  public static final String GRADLE_PROJECT_SETTINGS_FILE = "settings.gradle";

  /** Finds include ':module_name_1', ':module_name_2',... statements in settings.gradle files */
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("include +(':[^']+', *)*':[^']+'");

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
  public static final String TAG_VERSION = "version";
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
  public static final String ATTR_TO = "to";
  public static final String ATTR_FROM = "from";
  public static final String ATTR_AT = "at";
  public static final String ATTR_CONSTRAINTS = "constraints";

  public static final String CATEGORY_ACTIVITIES = "activities";
  public static final String CATEGORY_PROJECTS = "gradle-projects";

  /** The vendor ID of the support library. */
  private static final String VENDOR_ID = "android";
  /** The path ID of the support library. */
  private static final String SUPPORT_ID = "support";
  /** The path ID of the compatibility library (which was its id for releases 1-3). */
  private static final String COMPATIBILITY_ID = "compatibility";
  private static final String FD_V4 = "v4";
  private static final String ANDROID_SUPPORT_V4_JAR = "android-support-v4.jar";

  /** Support library constants */
  static final String SUPPORT_LIBRARY_NAME = "android-support";
  private static final String ANDROID_SUPPORT_URL = "androidSupportLibraryUrl";
  private static final String SUPPORT_BASE_URL = "com.android.support:support";
  private static final String SUFFIX_V4 = "-v4";
  private static final String SUFFIX_V7 = "-v7";
  private static final String SUFFIX_V13 = "-v13";
  private static final String MIN_VERSION_VALUE = "0.0.0";
  private static final String SUPPORT_REPOSITORY_PATH = "/extras/android/m2repository/com/android/support/support";
  private static final String MAVEN_METADATA_PATH = "/maven-metadata.xml";

  /**
   * List of files to open after the wizard has been created (these are
   * identified by {@link #TAG_OPEN} elements in the recipe file
   */
  private final List<String> myFilesToOpen = Lists.newArrayList();

  /** Path to the directory containing the templates */
  private final File myTemplateRoot;

  /* The base directory the template is expanded into */
  private File myOutputRoot;

  /* The directory of the module root for the project being worked with */
  private File myModuleRoot;

  /** The template loader which is responsible for finding (and sharing) template files */
  private final MyTemplateLoader myLoader;

  private TemplateMetadata myMetadata;

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
  @NotNull
  public void render(@NotNull File outputRootPath, @NotNull File moduleRootPath, @NotNull Map<String, Object> args) {
    assert outputRootPath.isDirectory() : outputRootPath;

    myFilesToOpen.clear();
    myOutputRoot = outputRootPath;
    myModuleRoot = moduleRootPath;

    Map<String, Object> paramMap = createParameterMap(args);
    Configuration freemarker = new Configuration();
    freemarker.setObjectWrapper(new DefaultObjectWrapper());
    freemarker.setTemplateLoader(myLoader);

    processFile(freemarker, TEMPLATE_XML, paramMap);
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
  private Map<String, Object> createParameterMap(@NotNull Map<String, Object> args) {
    // Create the data model.
    final Map<String, Object> paramMap = new HashMap<String, Object>();

    // Builtin conversion methods
    paramMap.put("slashedPackageName", new FmSlashedPackageNameMethod());
    paramMap.put("camelCaseToUnderscore", new FmCamelCaseToUnderscoreMethod());
    paramMap.put("underscoreToCamelCase", new FmUnderscoreToCamelCaseMethod());
    paramMap.put("activityToLayout", new FmActivityToLayoutMethod());
    paramMap.put("layoutToActivity", new FmLayoutToActivityMethod());
    paramMap.put("classToResource", new FmClassNameToResourceMethod());
    paramMap.put("escapeXmlAttribute", new FmEscapeXmlStringMethod());
    paramMap.put("escapeXmlText", new FmEscapeXmlStringMethod());
    paramMap.put("escapeXmlString", new FmEscapeXmlStringMethod());
    paramMap.put("extractLetters", new FmExtractLettersMethod());

    // This should be handled better: perhaps declared "required packages" as part of the
    // inputs? (It would be better if we could conditionally disable template based
    // on availability)
    Map<String, String> builtin = new HashMap<String, String>();
    builtin.put("templatesRes", VALUE_TEMPLATE_DIR);
    paramMap.put("android", builtin);

    // Wizard parameters supplied by user, specific to this template
    paramMap.putAll(args);

    return paramMap;
  }

  /** Read the given FreeMarker file and process the variable definitions */
  private void processFile(@NotNull final Configuration freemarker, @NotNull String path, @NotNull final Map<String, Object> paramMap) {
    try {
      String xml;
      if (path.endsWith(DOT_XML)) {
        // Just read the file
        xml = readTextFile(getTemplateFile(path));
        if (xml == null) {
          return;
        }
      } else {
        myLoader.setTemplateFile(getTemplateFile(path));
        xml = processFreemarkerTemplate(freemarker, paramMap, path);
      }

      // Handle UTF-8 since processed file may contain file paths
      ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8.toString()));
      Reader reader = new InputStreamReader(inputStream, Charsets.UTF_8.toString());
      InputSource inputSource = new InputSource(reader);
      inputSource.setEncoding(Charsets.UTF_8.toString());
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
              String value = attributes.getValue(ATTR_VALUE);
              paramMap.put(id, value);
            }
          } else if (TAG_GLOBALS.equals(name)) {
            // Handle evaluation of variables
            String path = attributes.getValue(ATTR_FILE);
            if (path != null) {
              processFile(freemarker, path, paramMap);
            } // else: <globals> root element
          } else if (TAG_EXECUTE.equals(name)) {
            String path = attributes.getValue(ATTR_FILE);
            if (path != null) {
              executeRecipeFile(freemarker, path, paramMap);
            }
          } else if (TAG_DEPENDENCY.equals(name)) {
            String dependencyName = attributes.getValue(ATTR_NAME);
            if (dependencyName.equals(SUPPORT_LIBRARY_NAME)) {
              // We assume the revision requirement has been satisfied
              // by the wizard
              int minApiLevel = (Integer)paramMap.get(TemplateMetadata.ATTR_MIN_API_LEVEL);
              paramMap.put(ANDROID_SUPPORT_URL, getSupportMavenUrl(minApiLevel));
            } // TODO: Add other libraries here (Cloud SDK, Play Services, AppCompatLib, etc).
          } else if (!name.equals("template") && !name.equals("category") && !name.equals("option") && !name.equals(TAG_THUMBS) &&
                     !name.equals(TAG_THUMB) && !name.equals(TAG_ICONS)) {
            LOG.error("WARNING: Unknown template directive " + name);
          }
        }
      });
    } catch (Exception e) {
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  /**
   * Calculate the correct version of the support library and generate the corresponding maven URL
   * @param minApiLevel the minimum api level specified by the template (-1 if no minApiLevel specified)
   * @return a maven url for the android support library
   */
  @Nullable
  private String getSupportMavenUrl(int minApiLevel) {
    String suffix = SUFFIX_V4;
    if (minApiLevel >= 13) {
      suffix = SUFFIX_V13;
    }


    // Read the support repository and find the latest version available
    String sdkLocation = AndroidSdkUtils.tryToChooseAndroidSdk().getLocation();
    String path = FileUtil.toSystemIndependentName(sdkLocation + SUPPORT_REPOSITORY_PATH + suffix + MAVEN_METADATA_PATH);
    File supportMetadataFile = new File(path);
    if (!supportMetadataFile.exists()) {
      Messages.showErrorDialog("You must install the Android Support Library though the SDK Manager.", "Support Repository Not Found");
      return null;
    }

    String version = getLatestVersionFromMavenMetadata(supportMetadataFile);

    return SUPPORT_BASE_URL + suffix + ":" + version;
  }

  /**
   * Parses a Maven metadata file and returns a string of the highest found version
   * @param metadataFile the files to parse
   * @return the string representing the highest version found in the file or "0.0.0" if no versions exist in the file
   */
  private static String getLatestVersionFromMavenMetadata(File metadataFile) {
    String xml = readTextFile(metadataFile);
    final List<FullRevision> versions = new LinkedList<FullRevision>();
    try {
      SAXParserFactory.newInstance().newSAXParser().parse(new ByteArrayInputStream(xml.getBytes()), new DefaultHandler() {
        boolean inVersionTag = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
          if (qName.equals(TAG_VERSION)) {
            inVersionTag = true;
          }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
          // Get the version and compare it to the current known max version
          if (inVersionTag) {
            versions.add(FullRevision.parseRevision(new String(ch, start, length)));
            inVersionTag = false;
          }
        }
      });
    } catch (Exception e) {
      ourMostRecentException = e;
      LOG.warn(e);
    }

    if (versions.isEmpty()) {
      return MIN_VERSION_VALUE;
    } else {
      return Collections.max(versions).toString();
    }
  }

  /** Executes the given recipe file: copying, merging, instantiating, opening files etc */
  private void executeRecipeFile(@NotNull final Configuration freemarker, @NotNull String file, @NotNull final Map<String,
    Object> paramMap) {
    try {
      myLoader.setTemplateFile(getTemplateFile(file));
      String xml = processFreemarkerTemplate(freemarker, paramMap, file);

      // Parse and execute the resulting instruction list. We handle UTF-8 since the processed file contains paths which may
      // have UTF-8 characters.
      ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8.toString()));
      Reader reader = new InputStreamReader(inputStream, Charsets.UTF_8.toString());
      InputSource inputSource = new InputSource(reader);
      inputSource.setEncoding(Charsets.UTF_8.toString());
      SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
          try {
            boolean instantiate = TAG_INSTANTIATE.equals(name);
            if (TAG_COPY.equals(name) || instantiate) {
              String fromPath = attributes.getValue(ATTR_FROM);
              String toPath = attributes.getValue(ATTR_TO);
              if (toPath == null || toPath.isEmpty()) {
                toPath = attributes.getValue(ATTR_FROM);
                toPath = TemplateUtils.stripSuffix(toPath, DOT_FTL);
              }
              if (instantiate) {
                instantiate(freemarker, paramMap, fromPath, toPath);
              }
              else {
                copyTemplateResource(fromPath, toPath);
              }
            }
            else if (TAG_MERGE.equals(name)) {
              String fromPath = attributes.getValue(ATTR_FROM);
              String toPath = attributes.getValue(ATTR_TO);
              if (toPath == null || toPath.isEmpty()) {
                toPath = attributes.getValue(ATTR_FROM);
                toPath = TemplateUtils.stripSuffix(toPath, DOT_FTL);
              }
              // Resources in template.xml are located within root/
              merge(freemarker, paramMap, fromPath, toPath);
            }
            else if (name.equals(TAG_OPEN)) {
              // The relative path here is within the output directory:
              String relativePath = attributes.getValue(ATTR_FILE);
              if (relativePath != null && !relativePath.isEmpty()) {
                myFilesToOpen.add(relativePath);
              }
            }
            else if (name.equals(TAG_MKDIR)) {
              // The relative path here is within the output directory:
              String relativePath = attributes.getValue(ATTR_AT);
              if (relativePath != null && !relativePath.isEmpty()) {
                mkdir(freemarker, paramMap, relativePath);
              }
            }
            else if (!name.equals("recipe")) {
              System.err.println("WARNING: Unknown template directive " + name);
            }
          }
          catch (Exception e) {
            ourMostRecentException = e;
            LOG.warn(e);
          }
        }
      });
    } catch (Exception e) {
      ourMostRecentException = e;
      LOG.warn(e);
    }
  }

  private void merge(@NotNull final Configuration freemarker,
                     @NotNull final Map<String, Object> paramMap,
                     @NotNull String relativeFrom,
                     @NotNull String toPath) throws IOException, TemplateException {

    String targetText = null;

    File to = getTargetFile(toPath);
    if (!(toPath.endsWith(EXT_XML) || to.getName().equals(GRADLE_PROJECT_SETTINGS_FILE))) {
      throw new RuntimeException("Only XML or Gradle build files can be merged at this point: " + to);
    }

    if (to.exists()) {
      targetText = Files.toString(to, Charsets.UTF_8);
    }

    if (targetText == null) {
      // The target file doesn't exist: don't merge, just copy
      boolean instantiate = relativeFrom.endsWith(DOT_FTL);
      if (instantiate) {
        instantiate(freemarker, paramMap, relativeFrom, toPath);
      } else {
        copyTemplateResource(relativeFrom, toPath);
      }
      return;
    }

    String sourceText = null;
    File from = getFullPath(relativeFrom);
    if (relativeFrom.endsWith(DOT_FTL)) {
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
      contents = mergeGradleSettingsFile(sourceText, targetText, freemarker, paramMap);
    } else {
      contents = mergeXml(sourceText, targetText, to, paramMap);
    }

    writeFile(contents, to);
  }

  private String mergeXml(String sourceXml, String targetXml, File targetFile, Map<String, Object> paramMap) {
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
        contents = XmlPrettyPrinter.prettyPrint(currentDocument, createXmlFormatPreferences(), formatStyle, null, targetXml.endsWith("\n"));
      }
    } else {
      // Just insert into file along with comment, using the "standard" conflict
      // syntax that many tools and editors recognize.
      String sep = SdkUtils.getLineSeparator();
      contents =
        "<<<<<<< Original" + sep
        + targetXml + sep
        + "=======" + sep
        + sourceXml
        + ">>>>>>> Added" + sep;
    }
    return contents;
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
          Element element = (Element) node;
          String name = getResourceId(element);
          Node replace = name != null ? old.get(name) : null;
          if (replace != null) {
            // There is an existing item with the same id: just replace it
            // ACTUALLY -- let's NOT change it.
            // Let's say you've used the activity wizard once, and it
            // emits some configuration parameter as a resource that
            // it depends on, say "padding". Then the user goes and
            // tweaks the padding to some other number.
            // Now running the wizard a *second* time for some new activity,
            // we should NOT go and set the value back to the template's
            // default!
            //root.replaceChild(node, replace);

            // ... ON THE OTHER HAND... What if it's a parameter class
            // (where the template rewrites a common attribute). Here it's
            // really confusing if the new parameter is not set. This is
            // really an error in the template, since we shouldn't have conflicts
            // like that, but we need to do something to help track this down.
            LOG.warn("Warning: Ignoring name conflict in resource file for name " + name);
          } else {
            root.appendChild(currentDocument.importNode(node, true));
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

  private String mergeGradleSettingsFile(@NotNull String source,
                                         @NotNull String dest,
                                         @NotNull final Configuration freemarker,
                                         @NotNull final Map<String, Object> paramMap) throws IOException, TemplateException {
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

  /** Instantiates the given template file into the given output file */
  private void instantiate(
    @NotNull final Configuration freemarker,
    @NotNull final Map<String, Object> paramMap,
    @NotNull String relativeFrom,
    @NotNull String to) throws IOException, TemplateException {
    // For now, treat extension-less files as directories... this isn't quite right
    // so I should refine this! Maybe with a unique attribute in the template file?
    boolean isDirectory = relativeFrom.indexOf('.') == -1;
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

  /** Creates a directory at the given path */
  private void mkdir(
    @NotNull final Configuration freemarker,
    @NotNull final Map<String, Object> paramMap,
    @NotNull String at) throws IOException, TemplateException {
    File targetFile = getTargetFile(at);
    VfsUtil.createDirectories(targetFile.getAbsolutePath());
  }

  @NotNull
  private File getFullPath(@NotNull String fromPath) {
    if (fromPath.startsWith(VALUE_TEMPLATE_DIR)) {
      return new File(getTemplateRootFolder(), RESOURCE_ROOT +
                                               File.separator +
                                               fromPath.substring(VALUE_TEMPLATE_DIR.length() + 1).replace('/', File.separatorChar));
    }
    return new File(myTemplateRoot, DATA_ROOT + File.separator + fromPath);
  }

  @NotNull
  private File getTargetFile(@NotNull String path) throws IOException {
    File p = new File(path);
    if (p.isAbsolute()) {
      return p;
    }
    return new File(myOutputRoot, path.replace('/', File.separatorChar));
  }

  @NotNull
  private File getTemplateFile(@NotNull String path) throws IOException {
    return new File(myTemplateRoot, path.replace('/', File.separatorChar));
  }

  @NotNull
  private String processFreemarkerTemplate(@NotNull Configuration freemarker, @NotNull Map<String, Object> paramMap, @NotNull String path)
    throws IOException, TemplateException {
    freemarker.template.Template inputsTemplate = freemarker.getTemplate(path);
    StringWriter out = new StringWriter();
    inputsTemplate.process(paramMap, out);
    out.flush();
    return out.toString();
  }

  /** Reads the given file as text. */
  @Nullable
  private static String readTextFile(@NotNull File file) {
    assert file.isAbsolute();
    try {
      return Files.toString(file, Charsets.UTF_8);
    } catch (IOException e) {
      LOG.warn(e);
      return null;
    }
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
  private static String format(@NotNull String contents, String to) {
    // TODO: Implement this
    return contents;
  }

  /** Copy a template resource */
  private final void copyTemplateResource(
    @NotNull String relativeFrom,
    @NotNull String output) throws IOException {
    copy(getFullPath(relativeFrom), getTargetFile(output));
  }

  /**
  * Copies the given source file into the given destination file (where the
  * source is allowed to be a directory, in which case the whole directory is
  * copied recursively)
  */
  private void copy(@NotNull File src, @NotNull File dest) throws IOException {
    if (src.isDirectory()) {
      FileUtil.copyDirContent(src, dest);
    } else {
      FileUtil.copyContent(src, dest);
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
      try {
        vf = LocalFileSystem.getInstance().findFileByIoFile(to.getParentFile()).createChildData(this, to.getName());
      } catch (NullPointerException e) {
        throw new IOException("Unable to create file " + to.getAbsolutePath());
      }
    }
    vf.setBinaryContent(contents.getBytes(Charsets.UTF_8));
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
        return file.toURI().toURL();
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
        SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkManager != null) {
          IAndroidTarget t = sdkManager.getTargetFromHashString(hashString);
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
}
