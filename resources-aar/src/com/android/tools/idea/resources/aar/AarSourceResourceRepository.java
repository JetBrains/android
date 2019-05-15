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
package com.android.tools.idea.resources.aar;

import static com.android.SdkConstants.ANDROID_NS_NAME;
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

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.resources.PatternBasedFileFilter;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceNameKeyedMap;
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
import com.android.resources.Arity;
import com.android.resources.Density;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.resources.aar.Base128InputStream.StreamFormatException;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
import org.jetbrains.annotations.TestOnly;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 */
public class AarSourceResourceRepository extends AbstractAarResourceRepository {
  private static final Logger LOG = Logger.getInstance(AarSourceResourceRepository.class);

  @NotNull protected final Path myResourceDirectoryOrFile;
  protected boolean myLoadedFromCache;
  /**
   * Protocol used for constructing {@link PathString}s returned by the {@link AarFileResourceItem#getSource()} method.
   */
  @NotNull private final String mySourceFileProtocol;
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the {@link AarFileResourceItem#getSource()} method.
   */
  @NotNull private final String myResourcePathPrefix;
  /**
   * Common prefix of URLs of all file resources. Used to compose resource URLs returned by
   * the {@link AarFileResourceItem#getValue()} method.
   */
  @NotNull private final String myResourceUrlPrefix;
  /** @see #getIdsFromRTxt(). */
  @Nullable private Set<String> myRTxtIds;
  /** The package name read on-demand from the manifest. */
  @NotNull private final NullableLazyValue<String> myManifestPackageName;

  protected AarSourceResourceRepository(@NotNull Loader loader) {
    super(loader.myNamespace, loader.myLibraryName);
    myResourceDirectoryOrFile = loader.myResourceDirectoryOrFile;
    mySourceFileProtocol = loader.getSourceFileProtocol();
    myResourcePathPrefix = loader.getResourcePathPrefix();
    myResourceUrlPrefix = loader.getResourceUrlPrefix();

    myManifestPackageName = NullableLazyValue.createValue(() -> {
      Path manifest = myResourceDirectoryOrFile.resolveSibling(FN_ANDROID_MANIFEST_XML);
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
   * Creates and loads a resource repository. Consider calling AarResourceRepositoryCache.getSourceRepository instead of this
   * method.
   *
   * @param resourceDirectory the directory containing resources
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull Path resourceDirectory, @NotNull String libraryName) {
    return create(resourceDirectory, null, ResourceNamespace.RES_AUTO, libraryName);
  }

  /**
   * Creates and loads a resource repository. Consider calling AarResourceRepositoryCache.getSourceRepository
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
    Path resDir = resourceFolder.getRoot().toPath();
    Preconditions.checkArgument(resDir != null);
    return create(resDir, resourceFolder.getResources(), ResourceNamespace.RES_AUTO, libraryName);
  }

  @NotNull
  private static AarSourceResourceRepository create(@NotNull Path resourceDirectory,
                                                    @Nullable Collection<PathString> resourceFilesAndFolders,
                                                    @NotNull ResourceNamespace namespace,
                                                    @NotNull String libraryName) {
    Loader loader = new Loader(resourceDirectory, resourceFilesAndFolders, namespace, libraryName);
    AarSourceResourceRepository repository = new AarSourceResourceRepository(loader);
    loader.loadRepositoryContents(repository);
    return repository;
  }

  @Override
  @NotNull
  Path getOrigin() {
    return myResourceDirectoryOrFile;
  }

  @TestOnly
  @NotNull
  public static AarSourceResourceRepository createForTest(@NotNull Path resourceDirectory,
                                                          @NotNull ResourceNamespace namespace,
                                                          @NotNull String libraryName) {
    return create(resourceDirectory, null, namespace, libraryName);
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
   * <p>When R.txt is present, the Ids obtained using {@link #getResources(ResourceNamespace, ResourceType)} by passing in
   * {@link ResourceType#ID} only contain a subset of Ids (top level ones like layout file names, and id resources in values
   * xml file). Ids declared inside layouts and menus (using "@+id/") are not included. This is done for efficiency. However,
   * such IDs can be obtained from the R.txt file. And hence, this collection includes all id names from the R.txt file, but
   * doesn't have the associated {@link ResourceItem} with it.
   *
   * <p>When R.txt is missing or cannot be parsed, layout and menu files are scanned for "@+id/" declarations and this method returns null.
   *
   * @see Loader#loadIdsFromRTxt
   */
  @Nullable
  public Set<String> getIdsFromRTxt() {
    return myRTxtIds;
  }

  @Override
  @NotNull
  final PathString getSourceFile(@NotNull String relativeResourcePath, boolean forFileResource) {
    return new PathString(mySourceFileProtocol, myResourcePathPrefix + relativeResourcePath);
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
  void writeToStream(@NotNull Base128OutputStream stream) throws IOException {
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
    stream.setStringCache(Maps.newHashMapWithExpectedSize(10000)); // Enable string instance sharing to minimize memory consumption.

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

    populatePublicResourcesMap();
    freezeResources();
  }

  private static void deleteIgnoringErrors(@NotNull Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
    }
  }

  @TestOnly
  boolean isLoadedFromCache() {
    return myLoadedFromCache;
  }

  // For debugging only.
  @Override
  @NotNull
  public String toString() {
    return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this)) + " for " + myResourceDirectoryOrFile;
  }

  protected static class Loader implements ResourceFileFilter {
    /** The set of attribute formats that is used when no formats are explicitly specified and the attribute is not a flag or enum. */
    private final Set<AttributeFormat> DEFAULT_ATTR_FORMATS = Sets.immutableEnumSet(
        AttributeFormat.BOOLEAN,
        AttributeFormat.COLOR,
        AttributeFormat.DIMENSION,
        AttributeFormat.FLOAT,
        AttributeFormat.FRACTION,
        AttributeFormat.INTEGER,
        AttributeFormat.REFERENCE,
        AttributeFormat.STRING);
    private final PatternBasedFileFilter myFileFilter = new PatternBasedFileFilter();

    @NotNull protected final Map<ResourceType, Set<String>> myPublicResources = new EnumMap<>(ResourceType.class);
    @NotNull private final ListMultimap<String, AarAttrResourceItem> myAttrs = ArrayListMultimap.create();
    @NotNull private final ListMultimap<String, AarAttrResourceItem> myAttrCandidates = ArrayListMultimap.create();
    @NotNull private final ListMultimap<String, AarStyleableResourceItem> myStyleables = ArrayListMultimap.create();
    @NotNull private ResourceVisibility myDefaultVisibility = ResourceVisibility.PRIVATE;
    @NotNull private final Map<FolderConfiguration, AarConfiguration> myConfigCache = new HashMap<>();
    @NotNull private final ValueResourceXmlParser myParser = new ValueResourceXmlParser();
    @NotNull private final XmlTextExtractor myTextExtractor = new XmlTextExtractor();
    @NotNull private final ResourceUrlParser myUrlParser = new ResourceUrlParser();
    // Used to keep track of resources defined in the current value resource file.
    @NotNull private final Table<ResourceType, String, AbstractAarValueResourceItem> myValueFileResources =
        Tables.newCustomTable(new EnumMap<>(ResourceType.class), () -> new LinkedHashMap<>());
    @NotNull protected final Path myResourceDirectoryOrFile;
    @NotNull private final ResourceNamespace myNamespace;
    @Nullable private final Collection<PathString> myResourceFilesAndFolders;
    @Nullable private final String myLibraryName;

    Loader(@NotNull Path resourceDirectoryOrFile, @Nullable Collection<PathString> resourceFilesAndFolders,
           @NotNull ResourceNamespace namespace,  @Nullable String libraryName) {
      myResourceDirectoryOrFile = resourceDirectoryOrFile;
      myNamespace = namespace;
      myResourceFilesAndFolders = resourceFilesAndFolders;
      myLibraryName = libraryName;
    }

    protected void loadRepositoryContents(@NotNull AarSourceResourceRepository repository) {
      try {
        if (Files.notExists(myResourceDirectoryOrFile)) {
          return; // Don't report errors if the resource directory doesn't exist. This happens in some tests.
        }

        loadPublicResourceNames();

        Set<String> ids = loadIdsFromRTxt();
        repository.myRTxtIds = ids;
        boolean shouldParseResourceIds = ids == null;

        List<Path> sourceFilesAndFolders = myResourceFilesAndFolders == null ?
                                           ImmutableList.of(myResourceDirectoryOrFile) :
                                           myResourceFilesAndFolders.stream().map(PathString::toPath).collect(Collectors.toList());
        List<Path> resourceFiles = findResourceFiles(sourceFilesAndFolders);
        for (Path file : resourceFiles) {
          FolderInfo folderInfo = FolderInfo.create(file.getParent().getFileName().toString());
          if (folderInfo != null) {
            AarConfiguration configuration = getAarConfiguration(repository, folderInfo.configuration);
            loadResourceFile(file, folderInfo, configuration, shouldParseResourceIds);
          }
        }

        processAttrsAndStyleables();
        repository.populatePublicResourcesMap();
        repository.freezeResources();
      }
      catch (Exception e) {
        LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
      }
    }

    @NotNull
    protected String getSourceFileProtocol() {
      return "file";
    }

    @NotNull
    protected String getResourcePathPrefix() {
      return myResourceDirectoryOrFile.toString() + File.separatorChar;
    }

    @NotNull
    protected String getResourceUrlPrefix() {
      return portableFileName(myResourceDirectoryOrFile.toString()) + '/';
    }

    /**
     * Loads resource IDs from R.txt file and returns the list of their names, if successful.
     *
     * @return the names of ID resources, or null if the file is missing or invalid
     */
    @Nullable
    protected Set<String> loadIdsFromRTxt() {
      Path rDotTxt = myResourceDirectoryOrFile.resolveSibling(FN_RESOURCE_TEXT);
      if (Files.exists(rDotTxt)) {
        try {
          SymbolTable symbolTable = SymbolIo.readFromAaptNoValues(rDotTxt.toFile(), null);
          return symbolTable.getSymbols()
              .row(ResourceType.ID)
              .values()
              .stream()
              .map(s -> s.getCanonicalName())
              .collect(Collectors.toSet());
        }
        catch (Exception e) {
          LOG.warn("Failed to load id resources from " + rDotTxt.toString(), e);
          return null;
        }
      }
      return null;
    }

    @Override
    public boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs) {
      return myFileFilter.isIgnored(fileOrDirectory.toString(), attrs.isDirectory());
    }

    protected void loadPublicResourceNames() {
      Path file = myResourceDirectoryOrFile.resolveSibling(FN_PUBLIC_TXT);
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
      for (IOException e : fileCollector.ioErrors) {
        LOG.error("Error loading resources from " + myResourceDirectoryOrFile.toString(), e);
      }
      Collections.sort(fileCollector.resourceFiles); // Make sure that the files are in canonical order.
      return fileCollector.resourceFiles;
    }

    @NotNull
    private AarConfiguration getAarConfiguration(@NotNull AarSourceResourceRepository repository,
                                                 @NotNull FolderConfiguration folderConfiguration) {
      AarConfiguration aarConfiguration = myConfigCache.get(folderConfiguration);
      if (aarConfiguration != null) {
        return aarConfiguration;
      }

      aarConfiguration = new AarConfiguration(repository, folderConfiguration);
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

    private static void addResourceItem(@NotNull AbstractAarResourceItem item) {
      item.getRepository().addResourceItem(item);
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
      ResourceType resourceType = item.getType();
      // Add attr and styleable resources to intermediate maps to post-process them in the processAttrsAndStyleables
      // method after all resources are loaded.
      if (resourceType == ResourceType.ATTR) {
        addAttr((AarAttrResourceItem)item, myAttrs);
      }
      else if (resourceType == ResourceType.STYLEABLE) {
        myStyleables.put(item.getName(), (AarStyleableResourceItem)item);
      }
      else {
        // For compatibility with resource merger code we add value resources first to a file-specific map,
        // then move them to the global resource table. In case when there are multiple definitions of
        // the same resource in a single XML file, this algorithm preserves only the last definition.
        myValueFileResources.put(resourceType, item.getName(), item);
      }
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
              String idValue = parser.getAttributeValue(i);
              if (idValue.startsWith(NEW_ID_PREFIX) && idValue.length() > NEW_ID_PREFIX.length()) {
                String resourceName = idValue.substring(NEW_ID_PREFIX.length());
                ResourceVisibility visibility = getVisibility(ResourceType.ID, resourceName);
                AarValueResourceItem item = new AarValueResourceItem(ResourceType.ID, resourceName, sourceFile, visibility, null);
                if (!resourceAlreadyDefined(item)) { // Don't create duplicate ID resources.
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
      String resourceName = getResourceName(file);
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

    /**
     * Resource name is the part of the file name before the first dot, e.g. for "tab_press.9.png" it is "tab_press".
     */
    @NotNull
    private static String getResourceName(@NotNull Path file) {
      String filename = file.getFileName().toString();
      int dotPos = filename.indexOf('.');
      return dotPos < 0 ? filename : filename.substring(0, dotPos);
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
      EnumMap<Arity, String> values = new EnumMap<>(Arity.class);
      forSubTags(TAG_ITEM, () -> {
        String quantityValue = myParser.getAttributeValue(null, ATTR_QUANTITY);
        if (quantityValue != null) {
          Arity quantity = Arity.getEnum(quantityValue);
          if (quantity != null) {
            String text = myTextExtractor.extractText(myParser, false);
            values.put(quantity, text);
          }
        }
      });
      ResourceVisibility visibility = getVisibility(ResourceType.PLURALS, name);
      AarPluralsResourceItem item = new AarPluralsResourceItem(name, sourceFile, visibility, values);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    @NotNull
    private AarValueResourceItem createStringItem(
        @NotNull ResourceType type, @NotNull String name, @NotNull AarSourceFile sourceFile, boolean withRowXml)
        throws IOException, XmlPullParserException {
      ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
      String text = type == ResourceType.ID ? null : myTextExtractor.extractText(myParser, withRowXml);
      String rawXml = type == ResourceType.ID ? null : myTextExtractor.getRawXml();
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
            // Mimic behavior of AAPT2 and put an attr reference inside a styleable resource.
            attrs.add(attr.getFormats().isEmpty() ? attr : attr.createReference());

            // Don't create top-level attr resources in a foreign namespace, or for attr references in the res-auto namespace.
            // The second condition is determined by the fact that the attr in the res-auto namespace may have an explicit definition
            // outside of this resource repository.
            if (attr.getNamespace().equals(myNamespace) && (myNamespace != ResourceNamespace.RES_AUTO || !attr.getFormats().isEmpty())) {
              addAttr(attr, myAttrCandidates);
            }
          }
          catch (XmlSyntaxException e) {
            LOG.error(e);
          }
        }
      });
      // AAPT2 treats all styleable resources as public.
      // See https://android.googlesource.com/platform/frameworks/base/+/master/tools/aapt2/ResourceParser.cpp#1539
      AarStyleableResourceItem item = new AarStyleableResourceItem(name, sourceFile, ResourceVisibility.PUBLIC, attrs);
      item.setNamespaceResolver(namespaceResolver);
      return item;
    }

    private static void addAttr(@NotNull AarAttrResourceItem attr, @NotNull ListMultimap<String, AarAttrResourceItem> map) {
      List<AarAttrResourceItem> attrs = map.get(attr.getName());
      int i = findResourceWithSameNameAndConfiguration(attr, attrs);
      if (i >= 0) {
        // Found a matching attr definition.
        AarAttrResourceItem existing = attrs.get(i);
        if (existing.getFormats().isEmpty() && !attr.getFormats().isEmpty()) {
          attrs.set(i, attr); // Use the new attr since it contains more information than the existing one.
        }
      }
      else {
        attrs.add(attr);
      }
    }

    /**
     * Adds attr definitions from {@link #myAttrs}, and attr definition candidates from {@link #myAttrCandidates}
     * if they don't match the attr definitions present in {@link #myAttrs}.
     */
    private void processAttrsAndStyleables() {
      for (AarAttrResourceItem attr : myAttrs.values()) {
        addAttrWithAdjustedFormats(attr);
      }

      for (AarAttrResourceItem attr : myAttrCandidates.values()) {
        List<AarAttrResourceItem> attrs = myAttrs.get(attr.getName());
        int i = findResourceWithSameNameAndConfiguration(attr, attrs);
        if (i < 0) {
          addAttrWithAdjustedFormats(attr);
        }
      }

      // Resolve attribute references where it can be done without loosing any data to reduce resource memory footprint.
      for (AarStyleableResourceItem styleable : myStyleables.values()) {
        addResourceItem(resolveAttrReferences(styleable));
      }
    }

    private void addAttrWithAdjustedFormats(@NotNull AarAttrResourceItem attr) {
      if (attr.getFormats().isEmpty()) {
        attr = new AarAttrResourceItem(attr.getName(), attr.getSourceFile(), attr.getVisibility(), attr.getDescription(),
                                       attr.getGroupName(), DEFAULT_ATTR_FORMATS, Collections.emptyMap(), Collections.emptyMap());
      }
      addResourceItem(attr);
    }

    /**
     * Checks if resource with the same name, type and configuration has already been defined.
     *
     * @param resource the resource to check
     * @return true if a matching resource already exists
     */
    private static boolean resourceAlreadyDefined(@NotNull AbstractAarResourceItem resource) {
      AbstractAarResourceRepository repository = resource.getRepository();
      List<ResourceItem> items = repository.getResources(resource.getNamespace(), resource.getType(), resource.getName());
      return findResourceWithSameNameAndConfiguration(resource, items) >= 0;
    }

    private static int findResourceWithSameNameAndConfiguration(
        @NotNull ResourceItem resource, @NotNull List<? extends ResourceItem> items) {
      for (int i = 0; i < items.size(); i++) {
        ResourceItem item = items.get(i);
        if (item.getConfiguration().equals(resource.getConfiguration())) {
          return i;
        }
      }
      return -1;
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
      return names != null && names.contains(getKeyForVisibilityLookup(resourceName)) ? ResourceVisibility.PUBLIC : myDefaultVisibility;
    }

    /**
     * Transforms the given resource name to a key for lookup in myPublicResources.
     */
    @NotNull
    protected String getKeyForVisibilityLookup(@NotNull String resourceName) {
      // In public.txt all resource names are transformed by replacing dots, colons and dashes with underscores.
      return ResourceNameKeyedMap.flattenResourceName(resourceName);
    }

    @NotNull
    private String getRelativePath(@NotNull Path file) {
      return portableFileName(myResourceDirectoryOrFile.relativize(file).toString());
    }

    @NotNull
    private Path getFile(@NotNull AarSourceFile sourceFile) {
      String relativePath = sourceFile.getRelativePath();
      Preconditions.checkArgument(relativePath != null);
      return myResourceDirectoryOrFile.resolve(relativePath);
    }
  }

  /** A filter used to select resource files when traversing the file system. */
  protected interface ResourceFileFilter {
    /** Returns true to skip the file or directory, or false to accept it. */
    boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs);
  }

  private static class ResourceFileCollector implements FileVisitor<Path> {
    @NotNull final List<Path> resourceFiles = new ArrayList<>();
    @NotNull final List<IOException> ioErrors = new ArrayList<>();
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
      ioErrors.add(exc);
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
    @NotNull private final Deque<Boolean> textInclusionState = new ArrayDeque<>();
    private boolean nontrivialRawXml;

    @NotNull
    String extractText(@NotNull XmlPullParser parser, boolean withRawXml) throws IOException, XmlPullParserException {
      text.setLength(0);
      rawXml.setLength(0);
      textInclusionState.clear();
      nontrivialRawXml = false;

      int elementDepth = parser.getDepth();
      int event;
      loop:
      do {
        event = parser.nextToken();
        switch (event) {
          case XmlPullParser.START_TAG: {
            String tagName = parser.getName();
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              boolean includeNestedText = getTextInclusionState();
              String example = parser.getAttributeValue(null, ATTR_EXAMPLE);
              if (example != null) {
                text.append('(').append(example).append(')');
                includeNestedText = false;
              }
              else {
                String id = parser.getAttributeValue(null, ATTR_ID);
                if (id != null && !id.equals("id")) {
                  text.append('$').append('{').append(id).append('}');
                  includeNestedText = false;
                }
              }
              textInclusionState.addLast(includeNestedText);
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
              textInclusionState.removeLast();
            }
            break;
          }

          case XmlPullParser.ENTITY_REF:
          case XmlPullParser.TEXT: {
            String textPiece = parser.getText();
            if (getTextInclusionState()) {
              text.append(textPiece);
            }
            if (withRawXml) {
              rawXml.append(textPiece);
            }
            break;
          }

          case XmlPullParser.CDSECT: {
            String textPiece = parser.getText();
            if (getTextInclusionState()) {
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

      return ValueXmlHelper.unescapeResourceString(text.toString(), false, true);
    }

    private boolean getTextInclusionState() {
      return textInclusionState.isEmpty() || textInclusionState.getLast();
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
