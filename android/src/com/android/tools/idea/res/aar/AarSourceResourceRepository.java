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
package com.android.tools.idea.res.aar;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_EAT_COMMENT;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_SKIP;
import static com.android.ide.common.resources.ResourceItem.ATTR_EXAMPLE;
import static com.android.ide.common.resources.ResourceItem.XLIFF_G_TAG;
import static com.android.ide.common.resources.ResourceItem.XLIFF_NAMESPACE_PREFIX;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.resources.DuplicateDataException;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.PatternBasedFileFilter;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourceRepositories;
import com.android.ide.common.resources.ResourceSet;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.util.PathString;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.projectmodel.ResourceFolder;
import com.android.resources.Density;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 */
public class AarSourceResourceRepository extends AbstractAarResourceRepository {
  private static final Logger LOG = Logger.getInstance(AarSourceResourceRepository.class);
  private static final PatternBasedFileFilter DEFAULT_FILE_FILTER = new PatternBasedFileFilter();

  @NotNull protected final Path myResourceDirectory;
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the {@link AarFileResourceItem#getSource()} method.
   */
  @NotNull private String myResourcePathPrefix;
  /**
   * Common prefix of URLs of all file resources. Used to compose resource URLs returned by
   * the {@link AarFileResourceItem#getValue()} method.
   */
  @NotNull private String myResourceUrlPrefix;
  /** @see #getIdsFromRTxt(). */
  @Nullable private Set<String> myRTxtIds;
  /** The package name read on-demand from the manifest. */
  @NotNull private final NullableLazyValue<String> myManifestPackageName;

  protected AarSourceResourceRepository(@NotNull Path resourceDirectory, @NotNull ResourceNamespace namespace,
                                        @Nullable Set<String> rTxtDeclaredIds, @NotNull String libraryName) {
    super(namespace, libraryName);
    myResourceDirectory = resourceDirectory;
    myResourcePathPrefix = myResourceDirectory.toString() + File.separatorChar;
    myResourceUrlPrefix = myResourcePathPrefix.replace(File.separatorChar, '/');
    myRTxtIds = rTxtDeclaredIds;

    myManifestPackageName = NullableLazyValue.createValue(() -> {
      Path manifest = myResourceDirectory.resolveSibling(FN_ANDROID_MANIFEST_XML);
      if (Files.notExists(manifest)) {
        return null;
      }

      try {
        ManifestData manifestData = AndroidManifestParser.parse(manifest);
        return manifestData.getPackage();
      }
      catch (IOException e) {
        LOG.error("Failed to read manifest " + manifest.toString() + " for library " + myLibraryName, e);
        return null;
      }
    });
  }

  /**
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#getSourceRepository} instead of this
   * method.
   *
   * @param resourceDirectory the directory containing resources
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull File resourceDirectory, @NotNull String libraryName) {
    return create(resourceDirectory, null, ResourceNamespace.RES_AUTO, libraryName);
  }

  /**
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#getSourceRepository} instead of this
   * method.
   *
   * @param resourceFolder location where the resource files located. It contains a resource directory and a resource list should be loaded.
   *                      A null or empty resource list indicates that all files contained in {@code resourceFolder#root} should be loaded
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  // TODO(sprigogin): Change the contract to remove special treatment of an empty resource file list.
  @NotNull
  public static AarSourceResourceRepository create(@NotNull ResourceFolder resourceFolder, @NotNull String libraryName) {
    File resDir = resourceFolder.getRoot().toFile();
    Preconditions.checkArgument(resDir != null);
    return create(resDir, resourceFolder.getResources(), ResourceNamespace.RES_AUTO, libraryName);
  }

  @NotNull
  private static AarSourceResourceRepository create(@NotNull File resourceDirectory,
                                                    @Nullable Collection<PathString> resourceFilesAndFolders,
                                                    @NotNull ResourceNamespace namespace,
                                                    @NotNull String libraryName) {
    Set<String> rTxtIds = loadIdsFromRTxt(resourceDirectory);
    AarSourceResourceRepository repository = new AarSourceResourceRepository(resourceDirectory.toPath(), namespace, rTxtIds, libraryName);
    repository.load(resourceFilesAndFolders);
    return repository;
  }

  private void loadUsingResourceMerger(@Nullable Collection<PathString> resourceFilesAndFolders) {
    try {
      ILogger logger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
      ResourceMerger merger = new ResourceMerger(0);

      ResourceSet resourceSet = new ResourceSet(myResourceDirectory.getFileName().toString(), getNamespace(), myLibraryName, false);
      if (myRTxtIds == null) {
        resourceSet.setShouldParseResourceIds(true);
      }

      // The resourceFiles collection contains resource files to be parsed.
      // If it's null or empty, all files in the resource folder should be parsed.
      if (resourceFilesAndFolders == null || resourceFilesAndFolders.isEmpty()) {
        resourceSet.addSource(myResourceDirectory.toFile());
      }
      else {
        for (PathString resourceFile : resourceFilesAndFolders) {
          resourceSet.addSource(resourceFile.toFile());
        }
      }
      resourceSet.setTrackSourcePositions(false);
      try {
        resourceSet.loadFromFiles(logger);
      }
      catch (DuplicateDataException e) {
        // This should not happen; resourceSet validation is disabled.
        assert false;
      }
      catch (MergingException e) {
        LOG.warn(e);
      }
      merger.addDataSet(resourceSet);
      ResourceRepositories.updateTableFromMerger(merger, myFullTable);
    }
    catch (Exception e) {
      LOG.error("Failed to load resources from " + myResourceDirectory.toString(), e);
    }
  }

  private void load(@Nullable Collection<PathString> resourceFilesAndFolders) {
    if (!StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.get()) {
      loadUsingResourceMerger(resourceFilesAndFolders);
      return;
    }

    try {
      boolean shouldParseResourceIds = myRTxtIds == null;

      List<Path> sourceFilesAndFolders = resourceFilesAndFolders == null || resourceFilesAndFolders.isEmpty() ?
          ImmutableList.of(myResourceDirectory) :
          resourceFilesAndFolders.stream().map(PathString::toPath).collect(Collectors.toList());
      Loader loader = new Loader();
      loader.load(sourceFilesAndFolders, shouldParseResourceIds);
    }
    catch (Exception e) {
      LOG.error("Failed to load resources from " + myResourceDirectory.toString(), e);
    }
  }

  private void addResourceItem(@NotNull ResourceItem item) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.getOrPutEmpty(myNamespace, item.getType());
    multimap.put(item.getName(), item);
  }

  /** A filter used to select resource files when traversing the file system. Subclasses may override. */
  @NotNull
  protected ResourceFileFilter getResourceFileFilter() {
    return (fileOrDirectory, attrs) -> !DEFAULT_FILE_FILTER.isIgnored(fileOrDirectory.toString(), attrs.isDirectory());
  }

  @VisibleForTesting
  @NotNull
  public static AarSourceResourceRepository createForTest(@NotNull File resourceDirectory,
                                                          @NotNull ResourceNamespace namespace,
                                                          @NotNull String libraryName) {
    assert ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode();
    return create(resourceDirectory, null, namespace, libraryName);
  }

  /**
   * Loads resource IDs from R.txt file and returns the list of their names, if successful.
   *
   * <p>This method can return null, if the file is missing or invalid. This should not be the case for AARs built
   * using a stable version of Android plugin for Gradle, but could happen for AARs built using other tools.
   *
   * @param resourceDirectory the resource directory of the AAR
   */
  @Nullable
  private static Set<String> loadIdsFromRTxt(@NotNull File resourceDirectory) {
    // Look for a R.txt file which describes the available id's; this is available both
    // in an exploded-aar folder as well as in the build-cache for AAR files.
    File parentDirectory = resourceDirectory.getParentFile();
    if (parentDirectory == null) {
      return null;
    }

    File rDotTxt = new File(parentDirectory, FN_RESOURCE_TEXT);
    if (rDotTxt.exists()) {
      try {
        SymbolTable symbolTable = SymbolIo.readFromAaptNoValues(rDotTxt, null);
        return symbolTable.getSymbols()
                                      .row(ResourceType.ID)
                                      .values()
                                      .stream()
                                      .map(s -> s.getCanonicalName())
                                      .collect(Collectors.toSet());
      }
      catch (Exception e) {
        LOG.warn("Failed to load id resources from " + rDotTxt.getPath(), e);
        return null;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getPackageName() {
    String packageName = myNamespace.getPackageName();
    return packageName == null ? myManifestPackageName.getValue() : packageName;
  }

  /**
   * Returns names of id resources found in the R.txt file if the directory referenced by this repository contained a valid one.
   *
   * <p>When R.txt is present, the Ids obtained using {@link #getResources(ResourceNamespace, ResourceType)} by passing in {@link
   * ResourceType#ID} only contain a subset of Ids (top level ones like layout file names, and id resources in values xml file). Ids
   * declared inside layouts and menus (using "@+id/") are not included. This is done for efficiency. However, such IDs can be obtained from
   * the R.txt file. And hence, this collection includes all id names from the R.txt file, but doesn't have the associated {@link
   * ResourceItem} with it.
   *
   * <p>When R.txt is missing or cannot be parsed, layout and menu files are scanned for "@+id/" declarations and this method returns null.
   *
   * @see #loadIdsFromRTxt(File)
   */
  @Nullable
  public Set<String> getIdsFromRTxt() {
    return myRTxtIds;
  }

  @Override
  @NotNull
  final PathString getPathString(@NotNull String relativeResourcePath) {
    return new PathString(myResourcePathPrefix + relativeResourcePath);
  }

  @Override
  @NotNull
  final String getResourceUrl(@NotNull String relativeResourcePath) {
    return myResourceUrlPrefix + relativeResourcePath;
  }

  // For debugging only.
  @Override
  @NotNull
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " for " + myResourceDirectory;
  }

  private class Loader {
    @NotNull final Map<FolderConfiguration, AarConfiguration> configCache = new HashMap<>();
    @NotNull final ValueResourceXmlParser parser = new ValueResourceXmlParser();
    @NotNull final XmlTextExtractor textExtractor = new XmlTextExtractor();
    @NotNull final Map<ResourceType, Set<String>> publicResources = new EnumMap<>(ResourceType.class);
    @NotNull ResourceVisibility defaultVisibility = ResourceVisibility.PUBLIC;

    public void load(@NotNull List<Path> sourceFilesAndFolders, boolean shouldParseResourceIds) {
      if (Files.notExists(myResourceDirectory)) {
        //LOG.warn("Resource directory " + myResourceDirectory.toString() + " does not exist");
        return;
      }

      loadPublicResourceNames();

      List<Path> resourceFiles = findResourceFiles(sourceFilesAndFolders);
      for (Path file : resourceFiles) {
        FolderInfo folderInfo = FolderInfo.create(file.getParent().getFileName().toString());
        if (folderInfo != null) {
          AarConfiguration configuration = getAarConfiguration(folderInfo.configuration);
          loadResourceFile(file, folderInfo, configuration, shouldParseResourceIds);
        }
      }
    }

    private void loadPublicResourceNames() {
      Path file = myResourceDirectory.resolveSibling(FN_PUBLIC_TXT);
      try (BufferedReader reader = Files.newBufferedReader(file)) {
        defaultVisibility = ResourceVisibility.PRIVATE;

        String line;
        while ((line = reader.readLine()) != null) {
          // Lines in public.txt have the following format: <resource_type> <resource_name>
          line = line.trim();
          int delimiterPos = line.indexOf(' ');
          if (delimiterPos > 0 && delimiterPos + 1 < line.length()) {
            ResourceType type = ResourceType.fromXmlTagName(line.substring(0, delimiterPos));
            if (type != null) {
              String name = line.substring(delimiterPos + 1);
              Set<String> names = publicResources.computeIfAbsent(type, t -> new HashSet<>());
              names.add(name);
            }
          }
        }
      }
      catch (NoSuchFileException e) {
        // The "public.txt" file does not exist - defaultVisibility will be PUBLIC.
      }
      catch (IOException e) {
        LOG.warn("Error reading " + file.toString(), e);
      }
    }

    @NotNull
    private List<Path> findResourceFiles(@NotNull List<Path> filesOrFolders) {
      ResourceFileCollector fileCollector = new ResourceFileCollector(getResourceFileFilter());
      for (Path file : filesOrFolders) {
        try {
          Files.walkFileTree(file, fileCollector);
        }
        catch (IOException e) {
          // All IOExceptions are logged by ResourceFileCollector.
        }
      }
      return fileCollector.resourceFiles;
    }

    @NotNull
    private AarConfiguration getAarConfiguration(@NotNull FolderConfiguration folderConfiguration) {
      AarConfiguration aarConfiguration = configCache.get(folderConfiguration);
      if (aarConfiguration != null) {
        return aarConfiguration;
      }

      aarConfiguration = new AarConfiguration(AarSourceResourceRepository.this, folderConfiguration);
      configCache.put(folderConfiguration, aarConfiguration);
      return aarConfiguration;
    }

    private void loadResourceFile(
        @NotNull Path file, @NotNull FolderInfo folderInfo, @NotNull AarConfiguration configuration, boolean shouldParseResourceIds) {
      if (folderInfo.resourceType == null) {
        parseValueResourceFile(file, configuration);
      }
      else {
        String fileName = file.getFileName().toString();
        if (shouldParseResourceIds && folderInfo.isIdGenerating && SdkUtils.endsWithIgnoreCase(fileName, DOT_XML)) {
          parseIdGeneratingResourceFile(file, configuration);
        }

        AarFileResourceItem item = createFileResourceItem(file, folderInfo.resourceType, configuration);
        addResourceItem(item);
      }
    }

    private void parseValueResourceFile(@NotNull Path file, @NotNull AarConfiguration configuration) {
      AarSourceFile sourceFile = new AarSourceFile(getRelativePath(file), configuration);

      try (InputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
        parser.setInput(stream, null);

        int event;
        do {
          event = parser.nextToken();
          int depth = parser.getDepth();
          if (event == XmlPullParser.START_TAG) {
            if (parser.getPrefix() != null) {
              continue;
            }
            String tagName = parser.getName();
            assert depth <= 2; // Deeper tags should be consumed by the createResourceItem method.
            if (depth == 1) {
              if (!tagName.equals(TAG_RESOURCES)) {
                break;
              }
            }
            else if (depth > 1) {
              ResourceType resourceType = getResourceType(tagName, file);
              if (resourceType != null && resourceType != ResourceType.PUBLIC) {
                String resourceName = parser.getAttributeValue(null, ATTR_NAME);
                if (resourceName != null) {
                  validateResourceName(resourceName, resourceType, file);
                  AbstractAarValueResourceItem item = createResourceItem(resourceType, resourceName, sourceFile);
                  addResourceItem(item);
                }
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT);
      }
      catch (IOException | XmlPullParserException | XmlSyntaxException e) {
        LOG.warn("Failed to parse " + file.toString(), e);
      }
    }

    private void parseIdGeneratingResourceFile(@NotNull Path file, @NotNull AarConfiguration configuration) {
      AarSourceFile sourceFile = new AarSourceFile(getRelativePath(file), configuration);

      try (InputStream stream = new BufferedInputStream(Files.newInputStream(file))) {
        XmlPullParser parser = new KXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(stream, null);

        int event;
        do {
          event = parser.nextToken();
          if (event == XmlPullParser.START_TAG) {
            int numAttributes = parser.getAttributeCount();
            for (int i = 0; i < numAttributes; i++) {
              if (NS_RESOURCES.equals(parser.getAttributeNamespace(i))) {
                String value = parser.getAttributeValue(i);
                if (value.startsWith(NEW_ID_PREFIX) && value.length() > NEW_ID_PREFIX.length()) {
                  String resourceName = value.substring(NEW_ID_PREFIX.length());
                  if (!hasIdenticalItem(ResourceType.ID, resourceName, configuration.getFolderConfiguration())) {
                    ResourceVisibility visibility = getVisibility(ResourceType.ID, resourceName);
                    AarValueResourceItem item = new AarValueResourceItem(ResourceType.ID, resourceName, sourceFile, visibility, null);
                    addResourceItem(item);
                  }
                }
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT);
      }
      catch (IOException | XmlPullParserException e) {
        LOG.warn("Failed to parse " + file.toString(), e);
      }
    }

    private boolean hasIdenticalItem(@NotNull ResourceType type, @NotNull String name, @NotNull FolderConfiguration configuration) {
      List<ResourceItem> items = getResources(myNamespace, type, name);
      for (ResourceItem item : items) {
        if (item.getConfiguration().equals(configuration)) {
          return true;
        }
      }
      return false;
    }

    @NotNull
    private AarFileResourceItem createFileResourceItem(@NotNull Path file, @NotNull ResourceType resourceType,
                                                       @NotNull AarConfiguration configuration) {
      String resourceName = Lint.getBaseName(file.getFileName().toString());
      ResourceVisibility visibility = getVisibility(ResourceType.ID, resourceName);
      String relativePath = getRelativePath(file);
      DensityQualifier densityQualifier = configuration.getFolderConfiguration().getDensityQualifier();
      if (densityQualifier != null) {
        Density densityValue = densityQualifier.getValue();
        if (densityValue != null) {
          return new AarDensityBasedFileResourceItem(resourceType, resourceName, configuration, visibility, relativePath, densityValue);
        }
      }
      return new AarFileResourceItem(resourceType, resourceName, configuration, visibility, relativePath);
    }

    @NotNull
    private AbstractAarValueResourceItem createResourceItem(
        @NotNull ResourceType type, @NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException, XmlSyntaxException {
      switch (type) {
        case ARRAY:
          return createArrayItem(name, sourceFile);

        case ATTR:
          return createAttrItem(name, sourceFile);

        case PLURALS:
          return createPluralsItem(name, sourceFile);

        case STRING:
          return createStringItem(type, name, sourceFile, true);

        case STYLE:
          return createStyleItem(name, sourceFile);

        case STYLEABLE:
          return createStyleableItem(name, sourceFile);

        case ANIMATOR:
        case DRAWABLE:
        case INTERPOLATOR:
        case LAYOUT:
        case MENU:
        case MIPMAP:
        case TRANSITION:
          return createFileReferenceItem(type, name, sourceFile);

        default:
          return createStringItem(type, name, sourceFile, false);
      }
    }

    @NotNull
    private AarArrayResourceItem createArrayItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      List<String> values = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        String text = textExtractor.extractText(parser, false);
        values.add(ValueXmlHelper.unescapeResourceString(text, false, true));
      });
      ResourceVisibility visibility = getVisibility(ResourceType.ARRAY, name);
      AarArrayResourceItem item = new AarArrayResourceItem(name, sourceFile, visibility, values);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarAttrResourceItem createAttrItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException, XmlSyntaxException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      ResourceNamespace attrNamespace = myNamespace;
      int colonPos = name.indexOf(':');
      if (colonPos >= 0) {
        if (colonPos == name.length() - 1) {
          throw new XmlSyntaxException("Invalid attr resource name \"" + name + "\"", parser, getFile(sourceFile));
        }
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
          attrNamespace = ResourceNamespace.ANDROID;
        } else {
          String prefix = name.substring(0, colonPos);
          attrNamespace = ResourceNamespace.fromNamespacePrefix(prefix, myNamespace, parser.getNamespaceResolver());
          if (attrNamespace == null) {
            throw new XmlSyntaxException("Undefined prefix of attr resource name \"" + name + "\"", parser, getFile(sourceFile));
          }
        }
        name = name.substring(colonPos + 1);
      }

      String description = parser.getLastComment();
      String formatString = parser.getAttributeValue(null, ATTR_FORMAT);
      Set<AttributeFormat> formats =
          StringUtil.isEmpty(formatString) ? EnumSet.noneOf(AttributeFormat.class) : AttributeFormat.parse(formatString);

      Map<String, Integer> valueMap = new HashMap<>();
      Map<String, String> valueDescriptionMap = new HashMap<>();
      forSubTags(null, () -> {
        if (parser.getPrefix() == null) {
          String tagName = parser.getName();
          AttributeFormat format =
              tagName.equals(TAG_ENUM) ? AttributeFormat.ENUM : tagName.equals(TAG_FLAG) ? AttributeFormat.FLAGS : null;
          if (format != null) {
            formats.add(format);
            String valueName = parser.getAttributeValue(null, ATTR_NAME);
            if (valueName != null) {
              String valueDescription = parser.getLastComment();
              if (valueDescription != null) {
                valueDescriptionMap.put(valueName, valueDescription);
              }
              String value = parser.getAttributeValue(null, ATTR_VALUE);
              Integer numericValue = null;
              if (value != null) {
                try {
                  // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we use Long.decode instead.
                  numericValue = Long.decode(value).intValue();
                }
                catch (NumberFormatException ignored) {
                }
              }
              valueMap.put(valueName, numericValue);
            }
          }
        }
      });

      AarAttrResourceItem item;
      if (attrNamespace.equals(myNamespace)) {
        ResourceVisibility visibility = getVisibility(ResourceType.ATTR, name);
        item = new AarAttrResourceItem(name, sourceFile, visibility, description, formats, valueMap, valueDescriptionMap);
      }
      else {
        item = new AarForeignAttrResourceItem(name, sourceFile, attrNamespace, description, formats, valueMap, valueDescriptionMap);
      }

      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarPluralsResourceItem createPluralsItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      List<String> quantities = new ArrayList<>();
      List<String> values = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        String quantity = parser.getAttributeValue(null, ATTR_QUANTITY);
        if (quantity != null) {
          quantities.add(quantity);
          String text = textExtractor.extractText(parser, false);
          values.add(ValueXmlHelper.unescapeResourceString(text, false, true));
        }
      });
      ResourceVisibility visibility = getVisibility(ResourceType.PLURALS, name);
      AarPluralsResourceItem item = new AarPluralsResourceItem(name, sourceFile, visibility, quantities, values);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarValueResourceItem createStringItem(
        @NotNull ResourceType type, @NotNull String name, @NotNull AarSourceFile sourceFile, boolean withRowXml)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      String text = textExtractor.extractText(parser, withRowXml);
      String rawXml = textExtractor.getRawXml();
      assert withRowXml || rawXml == null; // Text extractor doesn't extract raw XML unless asked to do it.
      ResourceVisibility visibility = getVisibility(type, name);
      AarValueResourceItem item = rawXml == null ?
          new AarValueResourceItem(type, name, sourceFile, visibility, text) :
          new AarTextValueResourceItem(type, name, sourceFile, visibility, text, rawXml);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarStyleResourceItem createStyleItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      String parentStyle = parser.getAttributeValue(null, ATTR_PARENT);
      List<StyleItemResourceValue> styleItems = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        ResourceNamespace.Resolver itemNamespaceResolver = parser.getNamespaceResolver();
        String itemName = parser.getAttributeValue(null, ATTR_NAME);
        if (itemName != null) {
          String text = textExtractor.extractText(parser, false);
          StyleItemResourceValueImpl styleItem = new StyleItemResourceValueImpl(myNamespace, itemName, text, myLibraryName);
          styleItem.setNamespaceResolver(itemNamespaceResolver);
          styleItems.add(styleItem);
        }
      });
      ResourceVisibility visibility = getVisibility(ResourceType.STYLE, name);
      AarStyleResourceItem item = new AarStyleResourceItem(name, sourceFile, visibility, parentStyle, styleItems);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarStyleableResourceItem createStyleableItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      List<AttrResourceValue> attrs = new ArrayList<>();
      forSubTags(TAG_ATTR, () -> {
        String attrName = parser.getAttributeValue(null, ATTR_NAME);
        if (attrName != null) {
          try {
            AarAttrResourceItem attr = createAttrItem(attrName, sourceFile);
            attrs.add(attr);
            // Don't create top-level attr resources in a foreign workspace or without any data.
            if (attr.getNamespace().equals(myNamespace) && !attr.getFormats().isEmpty() &&
                !hasIdenticalItem(ResourceType.ATTR, attrName, sourceFile.getConfiguration().getFolderConfiguration())) {
              addResourceItem(attr);
            }
          }
          catch (XmlSyntaxException e) {
            LOG.error(e);
          }
        }
      });
      ResourceVisibility visibility = getVisibility(ResourceType.STYLEABLE, name);
      AarStyleableResourceItem item = new AarStyleableResourceItem(name, sourceFile, visibility, attrs);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarValueResourceItem createFileReferenceItem(
        @NotNull ResourceType type, @NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = parser.getNamespaceResolver();
      String text = textExtractor.extractText(parser, false).trim();
      if (!text.isEmpty() && !text.startsWith(PREFIX_RESOURCE_REF) && !text.startsWith(PREFIX_THEME_REF)) {
        text = text.replace('/', File.separatorChar);
      }
      ResourceVisibility visibility = getVisibility(type, name);
      AarValueResourceItem item = new AarValueResourceItem(type, name, sourceFile, visibility, text);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @Nullable
    private ResourceType getResourceType(@NotNull String tagName, @NotNull Path file) throws XmlSyntaxException {
      ResourceType type = ResourceType.fromXmlTagName(tagName);

      if (type == null) {
        if (TAG_EAT_COMMENT.equals(tagName) || TAG_SKIP.equals(tagName)) {
          return null;
        }

        if (tagName.equals(TAG_ITEM)) {
          String typeAttr = parser.getAttributeValue(null, ATTR_TYPE);
          if (typeAttr != null) {
            type = ResourceType.fromClassName(typeAttr);
            if (type != null) {
              return type;
            }

            throw new XmlSyntaxException("Invalid type attribute \"" + typeAttr + "\"", parser, file);
          }
        }

        throw new XmlSyntaxException("Invalid tag name \"" + tagName + "\"", parser, file);
      }

      return type;
    }

    /**
     * If {@code tagName} is null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag.
     * If {@code tagName} is not null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag
     * which name doesn't have a prefix and matches {@code tagName}.
     */
    private void forSubTags(@Nullable String tagName, @NotNull XmlTagVisitor subtagVisitor) throws IOException, XmlPullParserException {
      int elementDepth = parser.getDepth();
      int event;
      do {
        event = parser.nextToken();
        if (event == XmlPullParser.START_TAG && (tagName == null || tagName.equals(parser.getName()) && parser.getPrefix() == null)) {
          subtagVisitor.visitTag();
        }
      } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || parser.getDepth() > elementDepth));
    }

    private void validateResourceName(@NotNull String resourceName, @NotNull ResourceType resourceType, @NotNull Path file)
        throws XmlSyntaxException {
      String error = ValueResourceNameValidator.getErrorText(resourceName, resourceType);
      if (error != null) {
        throw new XmlSyntaxException(error, parser, file);
      }
    }

    @NotNull
    private ResourceVisibility getVisibility(@NotNull ResourceType resourceType, @NotNull String resourceName) {
      Set<String> names = publicResources.get(resourceType);
      return names != null && names.contains(resourceName) ? ResourceVisibility.PUBLIC : defaultVisibility;
    }

    @NotNull
    private String getRelativePath(@NotNull Path file) {
      return myResourceDirectory.relativize(file).toString().replace(File.separatorChar, '/');
    }

    @NotNull
    private Path getFile(@NotNull AarSourceFile sourceFile) {
      String relativePath = sourceFile.getRelativePath();
      Preconditions.checkArgument(relativePath != null);
      return Paths.get(getResourceUrl(relativePath));
    }
  }

  /** A filter used to select resource files when traversing the file system. */
  protected interface ResourceFileFilter {
    /** Returns true to accept the file or directory, or false to skip it. */
    boolean test(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs);
  }

  private class ResourceFileCollector implements FileVisitor<Path> {
    @NotNull final List<Path> resourceFiles = new ArrayList<>();
    @NotNull final ResourceFileFilter fileFilter;

    private ResourceFileCollector(@NotNull ResourceFileFilter filter) {
      fileFilter = filter;
    }

    @Override
    @NotNull
    public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
      if (!fileFilter.test(dir, attrs)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NotNull
    public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
      if (!fileFilter.test(file, attrs)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      resourceFiles.add(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NotNull
    public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
      LOG.error("Error loading resources from " + myResourceDirectory.toString(), exc);
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NotNull
    public FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) {
      return FileVisitResult.CONTINUE;
    }
  }

  private interface XmlTagVisitor {
    /** Is called when the parser is positioned at a {@link XmlPullParser#START_TAG}. */
    void visitTag() throws IOException, XmlPullParserException;
  }

  /**
   * Information about a resource folder.
   */
  private static class FolderInfo {
    @NotNull final ResourceFolderType folderType;
    @NotNull final FolderConfiguration configuration;
    @Nullable final ResourceType resourceType;
    final boolean isIdGenerating;

    private FolderInfo(@NotNull ResourceFolderType folderType,
                       @NotNull FolderConfiguration configuration,
                       @Nullable ResourceType resourceType,
                       boolean isIdGenerating) {
      this.configuration = configuration;
      this.resourceType = resourceType;
      this.folderType = folderType;
      this.isIdGenerating = isIdGenerating;
    }

    /**
     * Returns a FolderInfo for the given folder name.
     *
     * @param folderName the name of a resource folder
     * @return the FolderInfo object, or null if folderName is not a valid name of a resource folder
     */
    @Nullable
    static FolderInfo create(@NotNull String folderName) {
      ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
      if (folderType == null) {
        return null;
      }

      FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(folderName);
      if (configuration == null) {
        return null;
      }

      if (!configuration.isDefault()) {
        configuration.normalize();
      }

      ResourceType resourceType;
      boolean isIdGenerating;
      if (folderType == ResourceFolderType.VALUES) {
        resourceType = null;
        isIdGenerating = false;
      }
      else {
        resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(folderType);
        isIdGenerating = FolderTypeRelationship.isIdGeneratingFolderType(folderType);
      }

      return new FolderInfo(folderType, configuration, resourceType, isIdGenerating);
    }
  }

  private static class XmlTextExtractor {
    @NotNull private final StringBuilder text = new StringBuilder();
    @NotNull private final StringBuilder rawXml = new StringBuilder();
    private boolean nontrivialRawXml;

    @NotNull
    String extractText(@NotNull XmlPullParser parser, boolean withRawXml) throws IOException, XmlPullParserException {
      text.setLength(0);
      rawXml.setLength(0);
      nontrivialRawXml = false;

      int elementDepth = parser.getDepth();
      int event;
      do {
        event = parser.nextToken();
        switch (event) {
          case XmlPullParser.START_TAG: {
            String tagName = parser.getName();
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              String example = parser.getAttributeValue(null, ATTR_EXAMPLE);
              if (example != null) {
                text.append('(').append(example).append(')');
              }
              else {
                String id = parser.getAttributeValue(null, ATTR_ID);
                if (id != null) {
                  text.append('$').append('{').append(id).append('}');
                }
              }
            }
            if (withRawXml) {
              nontrivialRawXml = true;
              rawXml.append('<');
              String prefix = parser.getPrefix();
              if (prefix != null) {
                rawXml.append(prefix).append(':');
              }
              rawXml.append(tagName);
              int numAttr = parser.getAttributeCount();
              for (int i = 0; i < numAttr; i++) {
                rawXml.append(' ');
                String attributePrefix = parser.getAttributePrefix(i);
                if (attributePrefix != null) {
                  rawXml.append(attributePrefix).append(':');
                }
                rawXml.append(parser.getAttributeName(i)).append('=').append('"');
                XmlUtils.appendXmlAttributeValue(rawXml, parser.getAttributeValue(i));
                rawXml.append('"');
              }
              rawXml.append('>');
            }
            break;
          }

          case XmlPullParser.END_TAG: {
            if (parser.getDepth() <= elementDepth) {
              return text.toString();
            }
            if (withRawXml) {
              rawXml.append('<').append('/');
              String prefix = parser.getPrefix();
              if (prefix != null) {
                rawXml.append(prefix).append(':');
              }
              rawXml.append(parser.getName()).append('>');
            }
            break;
          }

          case XmlPullParser.TEXT: {
            String textPiece = parser.getText();
            text.append(ValueXmlHelper.unescapeResourceString(textPiece, false, true));
            if (withRawXml) {
              rawXml.append(textPiece);
            }
            break;
          }

          case XmlPullParser.CDSECT: {
            String textPiece = parser.getText();
            text.append(textPiece);
            if (withRawXml) {
              nontrivialRawXml = true;
              rawXml.append("<!CDATA[").append(textPiece).append("]]>");
            }
            break;
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT);

      return text.toString();
    }

    @Nullable
    String getRawXml() {
      return nontrivialRawXml ? rawXml.toString() : null;
    }

    private static boolean isXliffNamespace(@Nullable String namespaceUri) {
      return namespaceUri != null && namespaceUri.startsWith(XLIFF_NAMESPACE_PREFIX);
    }
  }

  private static class XmlSyntaxException extends Exception {
    XmlSyntaxException(@NotNull String error, @NotNull XmlPullParser parser, @NotNull Path file) {
      super(error + " at " + file.toString() + " line " + parser.getLineNumber());
    }
  }
}
