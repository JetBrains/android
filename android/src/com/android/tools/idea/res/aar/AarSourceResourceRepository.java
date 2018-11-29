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

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
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
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.resources.PatternBasedFileFilter;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceVisitor;
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
import com.android.tools.idea.res.aar.Base128InputStream.StreamFormatException;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ObjectIntHashMap;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

  @NotNull protected final Path myResourceDirectory;
  protected boolean myLoadedFromCache;
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
                                        @Nullable Set<String> rTxtDeclaredIds, @Nullable String libraryName) {
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
        LOG.error("Failed to read manifest " + manifest.toString() + " for " + getDisplayName(), e);
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
   * Creates and loads a resource repository. Consider calling {@link AarResourceRepositoryCache#getSourceRepository}
   * instead of this method.
   *
   * @param resourceFolder specifies the resource files to be loaded. It contains a root resource directory and an optional
   *     list of files and subdirectories that should be loaded. A null {@code resourceFolder.getResources()} list indicates
   *     that all files contained in {@code resourceFolder.getRoot()} should be loaded.
   * @param libraryName the name of the library
   * @return the created resource repository
   */
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

  @Override
  @NotNull
  Path getOrigin() {
    return myResourceDirectory;
  }

  private void load(@Nullable Collection<PathString> resourceFilesAndFolders) {
    try {
      boolean shouldParseResourceIds = myRTxtIds == null;

      List<Path> sourceFilesAndFolders = resourceFilesAndFolders == null ?
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
    ListMultimap<String, ResourceItem> multimap = getOrCreateMap(item.getType());
    multimap.put(item.getName(), item);
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

  /**
   * Writes contents of an AAR resource repository to a cache file on disk.
   *
   * The data is stored as follows:
   * <ol>
   *   <li>The header provided by the caller (sequence of bytes)</li>
   *   <li>Number of folder configurations (int)</li>
   *   <li>Qualifier strings of folder configurations (strings)</li>
   *   <li>Number of value resource files (int)</li>
   *   <li>Value resource files (see {@link AarSourceFile#serialize})</li>
   *   <li>Number of namespace resolvers (int)</li>
   *   <li>Serialized namespace resolvers (see {@link NamespaceResolver#serialize})</li>
   *   <li>Number of resource items (int)</li>
   *   <li>Serialized resource items (see {@link AbstractAarResourceItem#serialize})</li>
   * </ol>
   */
  protected void createPersistentCache(@NotNull Path cacheFile, @NotNull byte[] fileHeader) {
    // Write to a temporary file first, then rename to to the final name.
    Path tempFile;
    try {
      Files.deleteIfExists(cacheFile);
      tempFile = FileUtilRt.createTempFile(cacheFile.getParent().toFile(), cacheFile.getFileName().toString(), ".tmp").toPath();
    }
    catch (IOException e) {
      LOG.error("Unable to create a temporary file in " + cacheFile.getParent().toString(), e);
      return;
    }

    try (Base128OutputStream stream = new Base128OutputStream(tempFile)) {
      stream.write(fileHeader);
      writeToStream(stream);
    }
    catch (Throwable e) {
      LOG.error("Unable to create cache file " + tempFile.toString(), e);
      deleteIgnoringErrors(tempFile);
      return;
    }

    try {
      Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (NoSuchFileException e) {
      // Ignore. This may happen in tests if the "caches" directory was cleaned up by a test tear down.
    } catch (IOException e) {
      LOG.error("Unable to create cache file " + cacheFile.toString(), e);
      deleteIgnoringErrors(tempFile);
    }
  }

  /**
   * Loads contents the repository from a cache file on disk.
   * @see #createPersistentCache(Path, byte[])
   */
  protected boolean loadFromPersistentCache(@NotNull Path cacheFile, @NotNull byte[] fileHeader) {
    try (Base128InputStream stream = new Base128InputStream(cacheFile)) {
      for (byte expected : fileHeader) {
        byte b = stream.readByte();
        if (b != expected) {
          return false; // Cache file header doesn't match.
        }
      }
      stream.setStringCache(Maps.newHashMapWithExpectedSize(10000)); // Enable string instance sharing to minimize memory consumption.
      loadFromStream(stream);
      myLoadedFromCache = true;
      return true;
    }
    catch (NoSuchFileException e) {
      return false; // Cache file does not exist.
    }
    catch (Throwable e) {
      cleanupAfterFailedLoadingFromCache();
      LOG.warn("Unable to load from cache file " + cacheFile.toString(), e);
      return false;
    }
  }

  /**
   * Called when an attempt to load from persistent cache fails after some data may have already been loaded.
   */
  protected void cleanupAfterFailedLoadingFromCache() {
    myResources.clear();  // Remove partially loaded data.
  }

  /**
   * Writes contents of the repository to the given output stream.
   */
  private void writeToStream(@NotNull Base128OutputStream stream) throws IOException {
    ObjectIntHashMap<String> qualifierStringIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<AarSourceFile> sourceFileIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes = new ObjectIntHashMap<>();
    accept(item -> {
      String qualifier = item.getConfiguration().getQualifierString();
      if (!qualifierStringIndexes.containsKey(qualifier)) {
        qualifierStringIndexes.put(qualifier, qualifierStringIndexes.size());
      }
      if (item instanceof AbstractAarValueResourceItem) {
        AarSourceFile sourceFile = ((AbstractAarValueResourceItem)item).getSourceFile();
        if (!sourceFileIndexes.containsKey(sourceFile)) {
          sourceFileIndexes.put(sourceFile, sourceFileIndexes.size());
        }
      }
      if (item instanceof ResourceValue) {
        ResourceNamespace.Resolver resolver = ((ResourceValue)item).getNamespaceResolver();
        if (!namespaceResolverIndexes.containsKey(resolver)) {
          namespaceResolverIndexes.put(resolver, namespaceResolverIndexes.size());
        }
      }
      return ResourceVisitor.VisitResult.CONTINUE;
    });

    writeStrings(qualifierStringIndexes, stream);
    writeSourceFiles(sourceFileIndexes, stream, qualifierStringIndexes);
    writeNamespaceResolvers(namespaceResolverIndexes, stream);

    Collection<ListMultimap<String, ResourceItem>> resourceMaps = myResources.values();
    int itemCount = 0;
    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      itemCount += resourceMap.size();
    }
    stream.writeInt(itemCount);

    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        ((AbstractAarResourceItem)item).serialize(stream, qualifierStringIndexes, sourceFileIndexes, namespaceResolverIndexes);
      }
    }
  }

  private static void writeStrings(@NotNull ObjectIntHashMap<String> qualifierStringIndexes, @NotNull Base128OutputStream stream)
      throws IOException {
    String[] strings = new String[qualifierStringIndexes.size()];
    qualifierStringIndexes.forEachEntry((str, index2) -> { strings[index2] = str; return true; });
    stream.writeInt(strings.length);
    for (String str : strings) {
      stream.writeString(str);
    }
  }

  private static void writeSourceFiles(@NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                                       @NotNull Base128OutputStream stream,
                                       @NotNull ObjectIntHashMap<String> qualifierStringIndexes) throws IOException {
    AarSourceFile[] sourceFiles = new AarSourceFile[sourceFileIndexes.size()];
    sourceFileIndexes.forEachEntry((sourceFile, index1) -> { sourceFiles[index1] = sourceFile; return true; });
    stream.writeInt(sourceFiles.length);
    for (AarSourceFile sourceFile : sourceFiles) {
      sourceFile.serialize(stream, qualifierStringIndexes);
    }
  }

  private static void writeNamespaceResolvers(@NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes,
                                              @NotNull Base128OutputStream stream) throws IOException {
    ResourceNamespace.Resolver[] resolvers = new ResourceNamespace.Resolver[namespaceResolverIndexes.size()];
    namespaceResolverIndexes.forEachEntry((resolver, index) -> { resolvers[index] = resolver; return true; });
    stream.writeInt(resolvers.length);
    for (ResourceNamespace.Resolver resolver : resolvers) {
      NamespaceResolver serializableResolver =
          resolver == ResourceNamespace.Resolver.EMPTY_RESOLVER ? NamespaceResolver.EMPTY : (NamespaceResolver)resolver;
      serializableResolver.serialize(stream);
    }
  }

  /**
   * Loads contents the repository from the given input stream.
   * @see #writeToStream(Base128OutputStream)
   */
  protected void loadFromStream(@NotNull Base128InputStream stream) throws IOException {
    int n = stream.readInt();
    List<AarConfiguration> configurations = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String configQualifier = stream.readString();
      if (configQualifier == null) {
        throw StreamFormatException.invalidFormat();
      }
      FolderConfiguration folderConfig = FolderConfiguration.getConfigForQualifierString(configQualifier);
      if (folderConfig == null) {
        throw StreamFormatException.invalidFormat();
      }
      configurations.add(new AarConfiguration(this, folderConfig));
    }

    n = stream.readInt();
    List<AarSourceFile> sourceFiles = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      AarSourceFile sourceFile = AarSourceFile.deserialize(stream, configurations);
      sourceFiles.add(sourceFile);
    }

    n = stream.readInt();
    List<ResourceNamespace.Resolver> namespaceResolvers = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      NamespaceResolver resolver = NamespaceResolver.deserialize(stream);
      namespaceResolvers.add(resolver);
    }

    n = stream.readInt();
    for (int i = 0; i < n; i++) {
      AbstractAarResourceItem item = AbstractAarResourceItem.deserialize(stream, configurations, sourceFiles, namespaceResolvers);
      addResourceItem(item);
    }
  }

  private static void deleteIgnoringErrors(@NotNull Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
    }
  }

  @VisibleForTesting
  boolean isLoadedFromCache() {
    return myLoadedFromCache;
  }

  // For debugging only.
  @Override
  @NotNull
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " for " + myResourceDirectory;
  }

  protected class Loader implements ResourceFileFilter {
    private final PatternBasedFileFilter myFileFilter = new PatternBasedFileFilter();

    @NotNull protected final Map<ResourceType, Set<String>> myPublicResources = new EnumMap<>(ResourceType.class);
    @NotNull protected ResourceVisibility myDefaultVisibility = ResourceVisibility.PRIVATE;
    @NotNull private final Map<FolderConfiguration, AarConfiguration> myConfigCache = new HashMap<>();
    @NotNull private final ValueResourceXmlParser myParser = new ValueResourceXmlParser();
    @NotNull private final XmlTextExtractor myTextExtractor = new XmlTextExtractor();
    @NotNull private final ResourceUrlParser myUrlParser = new ResourceUrlParser();
    // Used to keep track of resources defined in the current value resource file.
    @NotNull private final Table<ResourceType, String, AbstractAarValueResourceItem> myValueFileResources =
        Tables.newCustomTable(new EnumMap<>(ResourceType.class), () -> new LinkedHashMap<>());

    public void load(@NotNull List<Path> sourceFilesAndFolders, boolean shouldParseResourceIds) {
      if (Files.notExists(myResourceDirectory)) {
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

    @Override
    public boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs) {
      return myFileFilter.isIgnored(fileOrDirectory.toString(), attrs.isDirectory());
    }

    protected void loadPublicResourceNames() {
      Path file = myResourceDirectory.resolveSibling(FN_PUBLIC_TXT);
      try (BufferedReader reader = Files.newBufferedReader(file)) {
        String line;
        while ((line = reader.readLine()) != null) {
          // Lines in public.txt have the following format: <resource_type> <resource_name>
          line = line.trim();
          int delimiterPos = line.indexOf(' ');
          if (delimiterPos > 0 && delimiterPos + 1 < line.length()) {
            ResourceType type = ResourceType.fromXmlTagName(line.substring(0, delimiterPos));
            if (type != null) {
              String name = line.substring(delimiterPos + 1);
              Set<String> names = myPublicResources.computeIfAbsent(type, t -> new HashSet<>());
              names.add(name);
            }
          }
        }
      }
      catch (NoSuchFileException e) {
        myDefaultVisibility = ResourceVisibility.PUBLIC; // The "public.txt" file does not exist - defaultVisibility will be PUBLIC.
      }
      catch (IOException e) {
        LOG.warn("Error reading " + file.toString(), e);
      }
    }

    @NotNull
    private List<Path> findResourceFiles(@NotNull List<Path> filesOrFolders) {
      ResourceFileCollector fileCollector = new ResourceFileCollector(this);
      for (Path file : filesOrFolders) {
        try {
          Files.walkFileTree(file, fileCollector);
        }
        catch (IOException e) {
          // All IOExceptions are logged by ResourceFileCollector.
        }
      }
      Collections.sort(fileCollector.resourceFiles); // Make sure that the files are in canonical order.
      return fileCollector.resourceFiles;
    }

    @NotNull
    private AarConfiguration getAarConfiguration(@NotNull FolderConfiguration folderConfiguration) {
      AarConfiguration aarConfiguration = myConfigCache.get(folderConfiguration);
      if (aarConfiguration != null) {
        return aarConfiguration;
      }

      aarConfiguration = new AarConfiguration(AarSourceResourceRepository.this, folderConfiguration);
      myConfigCache.put(folderConfiguration, aarConfiguration);
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
        myParser.setInput(stream, null);

        int event;
        do {
          event = myParser.nextToken();
          int depth = myParser.getDepth();
          if (event == XmlPullParser.START_TAG) {
            if (myParser.getPrefix() != null) {
              continue;
            }
            String tagName = myParser.getName();
            assert depth <= 2; // Deeper tags should be consumed by the createResourceItem method.
            if (depth == 1) {
              if (!tagName.equals(TAG_RESOURCES)) {
                break;
              }
            }
            else if (depth > 1) {
              ResourceType resourceType = getResourceType(tagName, file);
              if (resourceType != null && resourceType != ResourceType.PUBLIC) {
                String resourceName = myParser.getAttributeValue(null, ATTR_NAME);
                if (resourceName != null) {
                  validateResourceName(resourceName, resourceType, file);
                  AbstractAarValueResourceItem item = createResourceItem(resourceType, resourceName, sourceFile);
                  addValueResourceItem(item);
                }
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT);
      }
      catch (IOException | XmlPullParserException | XmlSyntaxException e) {
        LOG.warn("Failed to parse " + file.toString(), e);
      }

      addValueFileResources();
    }

    private void addValueResourceItem(@NotNull AbstractAarValueResourceItem item) {
      // For compatibility with resource merger code we add value resources first to a file-specific map,
      // then move them to the global resource table. In case when there are multiple definitions of
      // the same resource in a single XML file, this algorithm preserves only the last definition.
      myValueFileResources.put(item.getType(), item.getName(), item);
    }

    private void addValueFileResources() {
      for (AbstractAarValueResourceItem item : myValueFileResources.values()) {
        addResourceItem(item);
      }
      myValueFileResources.clear();
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
              if (ANDROID_URI.equals(parser.getAttributeNamespace(i))) {
                String idValue = parser.getAttributeValue(i);
                if (idValue.startsWith(NEW_ID_PREFIX) && idValue.length() > NEW_ID_PREFIX.length()) {
                  String resourceName = idValue.substring(NEW_ID_PREFIX.length());
                  ResourceVisibility visibility = getVisibility(ResourceType.ID, resourceName);
                  AarValueResourceItem item = new AarValueResourceItem(ResourceType.ID, resourceName, sourceFile, visibility, null);
                  addValueResourceItem(item);
                }
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT);
      }
      catch (IOException | XmlPullParserException e) {
        LOG.warn("Failed to parse " + file.toString(), e);
      }

      addValueFileResources();
    }

    @NotNull
    private AarFileResourceItem createFileResourceItem(@NotNull Path file, @NotNull ResourceType resourceType,
                                                       @NotNull AarConfiguration configuration) {
      String resourceName = Lint.getBaseName(file.getFileName().toString());
      ResourceVisibility visibility = getVisibility(resourceType, resourceName);
      String relativePath = getRelativePath(file);
      if (DensityBasedResourceValue.isDensityBasedResourceType(resourceType)) {
        DensityQualifier densityQualifier = configuration.getFolderConfiguration().getDensityQualifier();
        if (densityQualifier != null) {
          Density densityValue = densityQualifier.getValue();
          if (densityValue != null) {
            return new AarDensityBasedFileResourceItem(resourceType, resourceName, configuration, visibility, relativePath, densityValue);
          }
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
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      List<String> values = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        String text = myTextExtractor.extractText(myParser, false);
        values.add(text);
      });
      ResourceVisibility visibility = getVisibility(ResourceType.ARRAY, name);
      AarArrayResourceItem item = new AarArrayResourceItem(name, sourceFile, visibility, values);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarAttrResourceItem createAttrItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException, XmlSyntaxException {
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      ResourceNamespace attrNamespace;
      myUrlParser.parseResourceUrl(name);
      if (myUrlParser.hasNamespacePrefix(ANDROID_NS_NAME)) {
        attrNamespace = ResourceNamespace.ANDROID;
      } else {
        String prefix = myUrlParser.getNamespacePrefix();
        attrNamespace = ResourceNamespace.fromNamespacePrefix(prefix, myNamespace, myParser.getNamespaceResolver());
        if (attrNamespace == null) {
          throw new XmlSyntaxException("Undefined prefix of attr resource name \"" + name + "\"", myParser, getFile(sourceFile));
        }
      }
      name = myUrlParser.getName();

      String description = myParser.getLastComment();
      String groupName = myParser.getAttrGroupComment();
      String formatString = myParser.getAttributeValue(null, ATTR_FORMAT);
      Set<AttributeFormat> formats =
          StringUtil.isEmpty(formatString) ? EnumSet.noneOf(AttributeFormat.class) : AttributeFormat.parse(formatString);

      // The average number of enum or flag values is 7 for Android framework, so start with small maps.
      Map<String, Integer> valueMap = Maps.newHashMapWithExpectedSize(8);
      Map<String, String> descriptionMap = Maps.newHashMapWithExpectedSize(8);
      forSubTags(null, () -> {
        if (myParser.getPrefix() == null) {
          String tagName = myParser.getName();
          AttributeFormat format =
              tagName.equals(TAG_ENUM) ? AttributeFormat.ENUM : tagName.equals(TAG_FLAG) ? AttributeFormat.FLAGS : null;
          if (format != null) {
            formats.add(format);
            String valueName = myParser.getAttributeValue(null, ATTR_NAME);
            if (valueName != null) {
              String valueDescription = myParser.getLastComment();
              if (valueDescription != null) {
                descriptionMap.put(valueName, valueDescription);
              }
              String value = myParser.getAttributeValue(null, ATTR_VALUE);
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
        item = new AarAttrResourceItem(name, sourceFile, visibility, description, groupName, formats, valueMap, descriptionMap);
      }
      else {
        item = new AarForeignAttrResourceItem(attrNamespace, name, sourceFile, description, groupName, formats, valueMap, descriptionMap);
      }

      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarPluralsResourceItem createPluralsItem(@NotNull String name, @NotNull AarSourceFile sourceFile)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      List<String> quantities = new ArrayList<>();
      List<String> values = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        String quantity = myParser.getAttributeValue(null, ATTR_QUANTITY);
        if (quantity != null) {
          quantities.add(quantity);
          String text = myTextExtractor.extractText(myParser, false);
          values.add(text);
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
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      String text = myTextExtractor.extractText(myParser, withRowXml);
      String rawXml = myTextExtractor.getRawXml();
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
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      String parentStyle = myParser.getAttributeValue(null, ATTR_PARENT);
      if (parentStyle != null && !parentStyle.isEmpty()) {
        myUrlParser.parseResourceUrl(parentStyle);
        myUrlParser.getQualifiedName();
      }
      List<StyleItemResourceValue> styleItems = new ArrayList<>();
      forSubTags(TAG_ITEM, () -> {
        ResourceNamespace.Resolver itemNamespaceResolver = myParser.getNamespaceResolver();
        String itemName = myParser.getAttributeValue(null, ATTR_NAME);
        if (itemName != null) {
          String text = myTextExtractor.extractText(myParser, false);
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
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      List<AttrResourceValue> attrs = new ArrayList<>();
      forSubTags(TAG_ATTR, () -> {
        String attrName = myParser.getAttributeValue(null, ATTR_NAME);
        if (attrName != null) {
          try {
            AarAttrResourceItem attr = createAttrItem(attrName, sourceFile);
            attrs.add(attr);
            // Don't create top-level attr resources in a foreign workspace or without any data.
            if (attr.getNamespace().equals(myNamespace) && !attr.getFormats().isEmpty()) {
              addValueResourceItem(attr);
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
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      String text = myTextExtractor.extractText(myParser, false).trim();
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
          String typeAttr = myParser.getAttributeValue(null, ATTR_TYPE);
          if (typeAttr != null) {
            type = ResourceType.fromClassName(typeAttr);
            if (type != null) {
              return type;
            }

            throw new XmlSyntaxException("Invalid type attribute \"" + typeAttr + "\"", myParser, file);
          }
        }

        throw new XmlSyntaxException("Invalid tag name \"" + tagName + "\"", myParser, file);
      }

      return type;
    }

    /**
     * If {@code tagName} is null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag.
     * If {@code tagName} is not null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag
     * which name doesn't have a prefix and matches {@code tagName}.
     */
    private void forSubTags(@Nullable String tagName, @NotNull XmlTagVisitor subtagVisitor) throws IOException, XmlPullParserException {
      int elementDepth = myParser.getDepth();
      int event;
      do {
        event = myParser.nextToken();
        if (event == XmlPullParser.START_TAG && (tagName == null || tagName.equals(myParser.getName()) && myParser.getPrefix() == null)) {
          subtagVisitor.visitTag();
        }
      } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || myParser.getDepth() > elementDepth));
    }

    private void validateResourceName(@NotNull String resourceName, @NotNull ResourceType resourceType, @NotNull Path file)
        throws XmlSyntaxException {
      String error = ValueResourceNameValidator.getErrorText(resourceName, resourceType);
      if (error != null) {
        throw new XmlSyntaxException(error, myParser, file);
      }
    }

    @NotNull
    private ResourceVisibility getVisibility(@NotNull ResourceType resourceType, @NotNull String resourceName) {
      Set<String> names = myPublicResources.get(resourceType);
      return names != null && names.contains(resourceName) ? ResourceVisibility.PUBLIC : myDefaultVisibility;
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
    /** Returns true to skip the file or directory, or false to accept it. */
    boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs);
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
      if (fileFilter.isIgnored(dir, attrs)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NotNull
    public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
      if (fileFilter.isIgnored(file, attrs)) {
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

      int xliffDepth = 0;
      int elementDepth = parser.getDepth();
      int event;
      loop:
      do {
        event = parser.nextToken();
        switch (event) {
          case XmlPullParser.START_TAG: {
            String tagName = parser.getName();
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              xliffDepth++;
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
              break loop;
            }
            String tagName = parser.getName();
            if (withRawXml) {
              rawXml.append('<').append('/');
              String prefix = parser.getPrefix();
              if (prefix != null) {
                rawXml.append(prefix).append(':');
              }
              rawXml.append(tagName).append('>');
            }
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              xliffDepth--;
            }
            break;
          }

          case XmlPullParser.ENTITY_REF:
          case XmlPullParser.TEXT: {
            String textPiece = parser.getText();
            if (xliffDepth == 0) {
              text.append(textPiece);
            }
            if (withRawXml) {
              rawXml.append(textPiece);
            }
            break;
          }

          case XmlPullParser.CDSECT: {
            String textPiece = parser.getText();
            if (xliffDepth == 0) {
              text.append(textPiece);
            }
            if (withRawXml) {
              nontrivialRawXml = true;
              rawXml.append("<!CDATA[").append(textPiece).append("]]>");
            }
            break;
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT);

      String result = ValueXmlHelper.unescapeResourceString(text.toString(), false, true);
      assert result != null;
      return result;
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
