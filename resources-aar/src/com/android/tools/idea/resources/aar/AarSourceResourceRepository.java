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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.AndroidManifestPackageNameUtils;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.symbols.SymbolIo;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ResourceFolder;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.idea.resources.base.Base128InputStream;
import com.android.tools.idea.resources.base.Base128OutputStream;
import com.android.tools.idea.resources.base.BasicFileResourceItem;
import com.android.tools.idea.resources.base.BasicResourceItemBase;
import com.android.tools.idea.resources.base.BasicValueResourceItemBase;
import com.android.tools.idea.resources.base.NamespaceResolver;
import com.android.tools.idea.resources.base.RepositoryConfiguration;
import com.android.tools.idea.resources.base.RepositoryLoader;
import com.android.tools.idea.resources.base.ResourceSourceFile;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A resource repository representing unpacked contents of a non-namespaced AAR.
 *
 * For performance reasons ID resources defined using @+id syntax in layout XML files are
 * obtained from R.txt instead, when it is available. This means that
 * {@link ResourceItem#getOriginalSource()} method may return null for such ID resources.
 */
public class AarSourceResourceRepository extends AbstractAarResourceRepository {
  /**
   * Increment when making changes that may affect content of repository cache files.
   * Used together with CachingData.codeVersion. Important for developer builds.
   */
  static final String CACHE_FILE_FORMAT_VERSION = "2";
  private static final byte[] CACHE_FILE_HEADER = "Resource cache".getBytes(UTF_8);
  private static final Logger LOG = Logger.getInstance(AarSourceResourceRepository.class);

  @NotNull protected final Path myResourceDirectoryOrFile;
  protected boolean myLoadedFromCache;
  /**
   * Protocol used for constructing {@link PathString}s returned by the {@link BasicFileResourceItem#getSource()} method.
   */
  @NotNull private final String mySourceFileProtocol;
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the {@link BasicFileResourceItem#getSource()} method.
   */
  @NotNull private final String myResourcePathPrefix;
  /**
   * Common prefix of URLs of all file resources. Used to compose resource URLs returned by
   * the {@link BasicFileResourceItem#getValue()} method.
   */
  @NotNull private final String myResourceUrlPrefix;
  /** The package name read on-demand from the manifest. */
  @NotNull private final NullableLazyValue<String> myManifestPackageName;

  protected AarSourceResourceRepository(@NotNull RepositoryLoader loader, @Nullable String libraryName) {
    super(loader.getNamespace(), libraryName);
    myResourceDirectoryOrFile = loader.getResourceDirectoryOrFile();
    mySourceFileProtocol = loader.getSourceFileProtocol();
    myResourcePathPrefix = loader.getResourcePathPrefix();
    myResourceUrlPrefix = loader.getResourceUrlPrefix();

    myManifestPackageName = NullableLazyValue.createValue(() -> {
      try {
        PathString manifestPath = getSourceFile("../" + FN_ANDROID_MANIFEST_XML, true);
        return AndroidManifestPackageNameUtils.getPackageNameFromManifestFile(manifestPath);
      }
      catch (FileNotFoundException e) {
        return null;
      }
      catch (IOException e) {
        LOG.error("Failed to read manifest " + FN_ANDROID_MANIFEST_XML + " for " + getDisplayName(), e);
        return null;
      }
    });
  }

  /**
   * Creates and loads a resource repository. Consider calling AarResourceRepositoryCache.getSourceRepository instead of this
   * method.
   *
   * @param resourceDirectoryOrFile the res directory or an AAR file containing resources
   * @param libraryName the name of the library
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull Path resourceDirectoryOrFile, @NotNull String libraryName) {
    return create(resourceDirectoryOrFile, libraryName, null);
  }

  /**
   * Creates and loads a resource repository. Consider calling AarResourceRepositoryCache.getSourceRepository instead of this
   * method.
   *
   * @param resourceDirectoryOrFile the res directory or an AAR file containing resources
   * @param libraryName the name of the library
   * @param cachingData data used to validate and create a persistent cache file
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull Path resourceDirectoryOrFile, @NotNull String libraryName,
                                                   @Nullable CachingData cachingData) {
    return create(resourceDirectoryOrFile, null, ResourceNamespace.RES_AUTO, libraryName, cachingData);
  }

  /**
   * Creates and loads a resource repository without using a persistent cache. Consider calling
   * AarResourceRepositoryCache.getSourceRepository instead of this method.
   *
   * @param resourceFolder specifies the resource files to be loaded. It contains a root resource directory and an optional
   *     list of files and subdirectories that should be loaded. A null {@code resourceFolder.getResources()} list indicates
   *     that all files contained in {@code resourceFolder.getRoot()} should be loaded.
   * @param libraryName the name of the library
   * @param cachingData data used to validate and create a persistent cache file
   * @return the created resource repository
   */
  @NotNull
  public static AarSourceResourceRepository create(@NotNull ResourceFolder resourceFolder, @NotNull String libraryName,
                                                   @Nullable CachingData cachingData) {
    Path resDir = resourceFolder.getRoot().toPath();
    Preconditions.checkArgument(resDir != null);
    return create(resDir, resourceFolder.getResources(), ResourceNamespace.RES_AUTO, libraryName, cachingData);
  }

  @NotNull
  private static AarSourceResourceRepository create(@NotNull Path resourceDirectoryOrFile,
                                                    @Nullable Collection<PathString> resourceFilesAndFolders,
                                                    @NotNull ResourceNamespace namespace,
                                                    @NotNull String libraryName,
                                                    @Nullable CachingData cachingData) {
    Loader loader = new Loader(resourceDirectoryOrFile, resourceFilesAndFolders, namespace);
    AarSourceResourceRepository repository = new AarSourceResourceRepository(loader, libraryName);

    // If loading from an AAR file, try to load from a cache file first.
    if (cachingData != null && resourceFilesAndFolders == null && repository.loadFromPersistentCache(cachingData)) {
      return repository;
    }

    loader.loadRepositoryContents(repository);

    repository.populatePublicResourcesMap();
    repository.freezeResources();

    if (cachingData != null && resourceFilesAndFolders == null) {
      Executor executor = cachingData.getCacheCreationExecutor();
      if (executor != null) {
        executor.execute(() -> repository.createPersistentCache(cachingData));
      }
    }
    return repository;
  }

  @Override
  @NotNull
  public Path getOrigin() {
    return myResourceDirectoryOrFile;
  }

  @TestOnly
  @NotNull
  public static AarSourceResourceRepository createForTest(
      @NotNull Path resourceDirectoryOrFile, @NotNull ResourceNamespace namespace, @NotNull String libraryName) {
    return create(resourceDirectoryOrFile, null, namespace, libraryName, null);
  }

  @Override
  @Nullable
  public String getPackageName() {
    String packageName = myNamespace.getPackageName();
    return packageName == null ? myManifestPackageName.getValue() : packageName;
  }

  @Override
  @NotNull
  public PathString getSourceFile(@NotNull String relativeResourcePath, boolean forFileResource) {
    return new PathString(mySourceFileProtocol, myResourcePathPrefix + relativeResourcePath);
  }

  @Override
  @NotNull
  public String getResourceUrl(@NotNull String relativeResourcePath) {
    return myResourceUrlPrefix + relativeResourcePath;
  }

  /**
   * Loads the resource repository from a binary cache file on disk.
   *
   * @return true if the repository was loaded from the cache, or false if the cache does not
   *     exist or is out of date
   * @see #createPersistentCache(CachingData)
   */
  private boolean loadFromPersistentCache(@NotNull CachingData cachingData) {
    byte[] header = getCacheFileHeader(stream -> writeCacheHeaderContent(cachingData, stream));
    return loadFromPersistentCache(cachingData.getCacheFile(), header);
  }

  /**
   * Creates persistent cache on disk for faster loading later.
   */
  private void createPersistentCache(@NotNull CachingData cachingData) {
    byte[] header = getCacheFileHeader(stream -> writeCacheHeaderContent(cachingData, stream));
    createPersistentCache(cachingData.getCacheFile(), header, config -> true);
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
   *   <li>Value resource files (see {@link ResourceSourceFile#serialize})</li>
   *   <li>Number of namespace resolvers (int)</li>
   *   <li>Serialized namespace resolvers (see {@link NamespaceResolver#serialize})</li>
   *   <li>Number of resource items (int)</li>
   *   <li>Serialized resource items (see {@link BasicResourceItemBase#serialize})</li>
   * </ol>
   */
  protected final void createPersistentCache(@NotNull Path cacheFile, @NotNull byte[] fileHeader,
                                             @NotNull Predicate<FolderConfiguration> configFilter) {
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
      writeToStream(stream, configFilter);
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

  @NotNull
  protected static byte[] getCacheFileHeader(@NotNull Base128StreamWriter headerWriter) {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    try (Base128OutputStream stream = new Base128OutputStream(header)) {
      headerWriter.write(stream);
    }
    catch (IOException e) {
      throw new Error("Internal error", e); // An IOException in the try block above indicates a bug.
    }
    return header.toByteArray();
  }

  protected void writeCacheHeaderContent(@NotNull CachingData cachingData, @NotNull Base128OutputStream stream) throws IOException {
    stream.write(CACHE_FILE_HEADER);
    stream.writeString(CACHE_FILE_FORMAT_VERSION);
    stream.writeString(myResourceDirectoryOrFile.toString());
    stream.writeString(cachingData.getContentVersion());
    stream.writeString(cachingData.getCodeVersion());
  }

  /**
   * Loads contents the repository from a cache file on disk.
   * @see #createPersistentCache(Path, byte[], Predicate<FolderConfiguration>)
   */
  private boolean loadFromPersistentCache(@NotNull Path cacheFile, @NotNull byte[] fileHeader) {
    try (Base128InputStream stream = new Base128InputStream(cacheFile)) {
      if (!validateHeader(fileHeader, stream)) {
        return false; // Cache file header doesn't match.
      }
      loadFromStream(stream, Maps.newHashMapWithExpectedSize(1000), null);

      populatePublicResourcesMap();
      freezeResources();
      myLoadedFromCache = true;
      return true;
    }
    catch (NoSuchFileException e) {
      return false; // Cache file does not exist.
    }
    catch (Throwable e) {
      cleanupAfterFailedLoadingFromCache();
      LOG.warn("Unable to load resources from cache file " + cacheFile.toString(), e);
      return false;
    }
  }

  protected static boolean validateHeader(@NotNull byte[] fileHeader, @NotNull Base128InputStream stream) throws IOException {
    for (byte expected : fileHeader) {
      byte b = stream.readByte();
      if (b != expected) {
        return false;
      }
    }
    return true;
  }

  /**
   * Called when an attempt to load from persistent cache fails after some data may have already been loaded.
   */
  protected void cleanupAfterFailedLoadingFromCache() {
    myResources.clear();  // Remove partially loaded data.
  }

  /**
   * Writes contents of the repository to the given output stream.
   *
   * @param stream the stream to write to
   * @param configFilter only resources belonging to configurations satisfying this filter are written to the stream
   */
  void writeToStream(@NotNull Base128OutputStream stream, @NotNull Predicate<FolderConfiguration> configFilter) throws IOException {
    ObjectIntHashMap<String> qualifierStringIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<ResourceSourceFile> sourceFileIndexes = new ObjectIntHashMap<>();
    ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes = new ObjectIntHashMap<>();
    int itemCount = 0;
    Collection<ListMultimap<String, ResourceItem>> resourceMaps = myResources.values();

    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        FolderConfiguration configuration = item.getConfiguration();
        if (configFilter.test(configuration)) {
          String qualifier = configuration.getQualifierString();
          if (!qualifierStringIndexes.containsKey(qualifier)) {
            qualifierStringIndexes.put(qualifier, qualifierStringIndexes.size());
          }
          if (item instanceof BasicValueResourceItemBase) {
            ResourceSourceFile sourceFile = ((BasicValueResourceItemBase)item).getSourceFile();
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
          itemCount++;
        }
      }
    }

    writeStrings(qualifierStringIndexes, stream);
    writeSourceFiles(sourceFileIndexes, stream, qualifierStringIndexes);
    writeNamespaceResolvers(namespaceResolverIndexes, stream);

    stream.writeInt(itemCount);

    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        FolderConfiguration configuration = item.getConfiguration();
        if (configFilter.test(configuration)) {
          ((BasicResourceItemBase)item).serialize(stream, qualifierStringIndexes, sourceFileIndexes, namespaceResolverIndexes);
        }
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

  private static void writeSourceFiles(@NotNull ObjectIntHashMap<ResourceSourceFile> sourceFileIndexes,
                                       @NotNull Base128OutputStream stream,
                                       @NotNull ObjectIntHashMap<String> qualifierStringIndexes) throws IOException {
    ResourceSourceFile[] sourceFiles = new ResourceSourceFile[sourceFileIndexes.size()];
    sourceFileIndexes.forEachEntry((sourceFile, index1) -> { sourceFiles[index1] = sourceFile; return true; });
    stream.writeInt(sourceFiles.length);
    for (ResourceSourceFile sourceFile : sourceFiles) {
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
   * @see #writeToStream(Base128OutputStream, Predicate)
   */
  protected void loadFromStream(@NotNull Base128InputStream stream,
                                @NotNull Map<String, String> stringCache,
                                @Nullable Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache) throws IOException {
    stream.setStringCache(stringCache); // Enable string instance sharing to minimize memory consumption.

    int n = stream.readInt();
    List<RepositoryConfiguration> configurations = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String configQualifier = stream.readString();
      if (configQualifier == null) {
        throw Base128InputStream.StreamFormatException.invalidFormat();
      }
      FolderConfiguration folderConfig = FolderConfiguration.getConfigForQualifierString(configQualifier);
      if (folderConfig == null) {
        throw Base128InputStream.StreamFormatException.invalidFormat();
      }
      configurations.add(new RepositoryConfiguration(this, folderConfig));
    }

    n = stream.readInt();
    List<ResourceSourceFile> newSourceFiles = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      ResourceSourceFile sourceFile = ResourceSourceFile.deserialize(stream, configurations);
      newSourceFiles.add(sourceFile);
    }

    n = stream.readInt();
    List<ResourceNamespace.Resolver> newNamespaceResolvers = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      NamespaceResolver namespaceResolver = NamespaceResolver.deserialize(stream);
      if (namespaceResolverCache != null) {
        namespaceResolver = namespaceResolverCache.computeIfAbsent(namespaceResolver, Function.identity());
      }
      newNamespaceResolvers.add(namespaceResolver);
    }

    n = stream.readInt();
    for (int i = 0; i < n; i++) {
      BasicResourceItemBase item = BasicResourceItemBase.deserialize(stream, configurations, newSourceFiles, newNamespaceResolvers);
      addResourceItem(item);
    }
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

  private static class Loader extends RepositoryLoader<AarSourceResourceRepository> {
    @NotNull private Set<String> myRTxtIds = ImmutableSet.of();

    Loader(@NotNull Path resourceDirectoryOrFile, @Nullable Collection<PathString> resourceFilesAndFolders,
           @NotNull ResourceNamespace namespace) {
      super(resourceDirectoryOrFile, resourceFilesAndFolders, namespace);
    }

    @Override
    protected boolean loadIdsFromRTxt() {
      if (myZipFile == null) {
        Path rDotTxt = myResourceDirectoryOrFile.resolveSibling(FN_RESOURCE_TEXT);
        if (Files.exists(rDotTxt)) {
          try {
            SymbolTable symbolTable = SymbolIo.readFromAaptNoValues(rDotTxt.toFile(), null);
            myRTxtIds = computeIds(symbolTable);
            return true;
          }
          catch (Exception e) {
            LOG.warn("Failed to load id resources from " + rDotTxt.toString(), e);
          }
        }
      }
      else {
        ZipEntry zipEntry = myZipFile.getEntry(FN_RESOURCE_TEXT);
        if (zipEntry != null) {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(myZipFile.getInputStream(zipEntry), UTF_8))) {
            SymbolTable symbolTable = SymbolIo.readFromAaptNoValues(reader, FN_RESOURCE_TEXT + " in " + myResourceDirectoryOrFile, null);
            myRTxtIds = computeIds(symbolTable);
            return true;
          }
          catch (Exception e) {
            LOG.warn("Failed to load id resources from " + FN_RESOURCE_TEXT + " in " + myResourceDirectoryOrFile, e);
          }
        }
        return false;
      }
      return false;
    }

    @Override
    protected void finishLoading(@NotNull AarSourceResourceRepository repository) {
      super.finishLoading(repository);
      createResourcesForRTxtIds(repository);
    }

    /**
     * Creates ID resources for the ID names in the R.txt file.
     */
    private void createResourcesForRTxtIds(@NotNull AarSourceResourceRepository repository) {
      if (!myRTxtIds.isEmpty()) {
        RepositoryConfiguration configuration = getConfiguration(repository, FolderConfiguration.createDefault());
        ResourceSourceFile sourceFile = new ResourceSourceFile(null, configuration);
        for (String name : myRTxtIds) {
          addIdResourceItem(name, sourceFile);
        }
        addValueFileResources();
      }
    }

    private static Set<String> computeIds(@NotNull SymbolTable symbolTable) {
      return symbolTable.getSymbols()
        .row(ResourceType.ID)
        .values()
        .stream()
        .map(s -> s.getCanonicalName())
        .collect(Collectors.toSet());
    }

    @Override
    protected void loadPublicResourceNames() {
      if (myZipFile == null) {
        Path file = myResourceDirectoryOrFile.resolveSibling(FN_PUBLIC_TXT);
        try (BufferedReader reader = Files.newBufferedReader(file)) {
          readPublicResourceNames(reader);
        }
        catch (NoSuchFileException e) {
          myDefaultVisibility = ResourceVisibility.PUBLIC; // The "public.txt" file does not exist - myDefaultVisibility will be PUBLIC.
        }
        catch (IOException e) {
          // Failure to load public resource names is not considered fatal.
          LOG.warn("Error reading " + file.toString(), e);
        }
      } else {
        ZipEntry zipEntry = myZipFile.getEntry(FN_PUBLIC_TXT);
        if (zipEntry == null) {
          myDefaultVisibility = ResourceVisibility.PUBLIC; // The "public.txt" file does not exist - myDefaultVisibility will be PUBLIC.
        }
        else {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(myZipFile.getInputStream(zipEntry), UTF_8))) {
            readPublicResourceNames(reader);
          }
          catch (IOException e) {
            // Failure to load public resource names is not considered fatal.
            LOG.warn("Error reading " + FN_PUBLIC_TXT + " from " + myResourceDirectoryOrFile, e);
          }
        }
      }
    }

    @Override
    protected void addResourceItem(@NotNull BasicResourceItemBase item, @NotNull AarSourceResourceRepository repository) {
      repository.addResourceItem(item);
    }

    private void readPublicResourceNames(@NotNull BufferedReader reader) throws IOException {
      String line;
      while ((line = reader.readLine()) != null) {
        // Lines in public.txt have the following format: <resource_type> <resource_name>
        line = line.trim();
        int delimiterPos = line.indexOf(' ');
        if (delimiterPos > 0 && delimiterPos + 1 < line.length()) {
          ResourceType type = ResourceType.fromXmlTagName(line.substring(0, delimiterPos));
          if (type != null) {
            String name = line.substring(delimiterPos + 1);
            addPublicResourceName(type, name);
          }
        }
      }
    }
  }

  protected interface Base128StreamWriter {
    void write(@NotNull Base128OutputStream stream) throws IOException;
  }
}
