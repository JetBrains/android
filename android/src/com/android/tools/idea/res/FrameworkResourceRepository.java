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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.log.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.Hashing;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.w3c.dom.*;
import org.xmlpull.v1.XmlPullParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static com.google.common.collect.Sets.newLinkedHashSetWithExpectedSize;

/**
 * Repository of resources of the Android framework.
 *
 * <p>This repository behaves similar to {@link FileResourceRepository} except that it differentiates
 * between resources that are public and non public. {@link #getPublicResourcesOfType(ResourceType)}
 * can be used to obtain only public resources. This is typically used to display resource lists in
 * the UI.
 *
 * <p>For performance the repository, when possible, is loaded from a binary cache file located
 * under the directory returned by the {@link PathManager#getSystemPath()} method.
 * Loading from a cache file is 7-8 times faster than reading XML files.
 *
 * <p>For safety we don't assume any compatibility of cache file format between different versions
 * of the Android plugin. For the built-in framework resources used by LayoutLib this also guarantees
 * freshness of the cache when the Android plugin is updated.
 */
public final class FrameworkResourceRepository extends FileResourceRepository {
  private static final ResourceNamespace NAMESPACE = ResourceNamespace.ANDROID;
  private static final String CACHE_DIRECTORY = "framework_resource_cache";
  private static final String CACHE_FILE_HEADER = "Framework resource cache";
  private static final String CACHE_FILE_FORMAT_VERSION = "1";
  private static final String ANDROID_PLUGIN_ID = "org.jetbrains.android";
  private static final Logger LOG = Logger.getInstance(FrameworkResourceRepository.class);

  /** Namespace prefixes used in framework resources and the corresponding URIs. */
  private static final String[] WELL_KNOWN_NAMESPACES = new String[] {
    SdkConstants.ANDROID_NS_NAME, SdkConstants.ANDROID_URI,
    SdkConstants.XLIFF_PREFIX, SdkConstants.XLIFF_URI,
    SdkConstants.TOOLS_PREFIX, SdkConstants.TOOLS_URI,
    SdkConstants.AAPT_PREFIX, SdkConstants.AAPT_URI
  };

  private final boolean myWithLocaleResources;
  private final Map<ResourceType, List<ResourceItem>> myPublicResources = new EnumMap<>(ResourceType.class);
  private boolean myLoadedFromCache;

  private FrameworkResourceRepository(@NotNull File resFolder, boolean withLocaleResources) {
    super(resFolder, NAMESPACE, null);
    myWithLocaleResources = withLocaleResources;
  }

  /**
   * Creates an Android framework resource repository.
   *
   * @param resFolder the folder containing resources of the Android framework
   * @param withLocaleResources whether to include locale-specific resources or not
   * @param usePersistentCache whether the repository should attempt loading from
   *     a persistent cache and create the cache if it does not exist
   * @return the created resource repository
   */
  @NotNull
  public static FrameworkResourceRepository create(@NotNull File resFolder, boolean withLocaleResources, boolean usePersistentCache) {
    FrameworkResourceRepository repository = new FrameworkResourceRepository(resFolder, withLocaleResources);
    // Try to load from file cache first. Loading from cache is significantly faster than reading resource files.
    if (usePersistentCache && repository.loadFromPersistentCache()) {
      return repository;
    }

    ResourceSet resourceSet = new FrameworkResourceSet(resFolder, withLocaleResources);

    try {
      ILogger logger = new LogWrapper(LOG).alwaysLogAsDebug(true).allowVerbose(false);
      resourceSet.loadFromFiles(logger);
    }
    catch (DuplicateDataException e) {
      // This should not happen; resourceSet validation is disabled.
      assert false;
    }
    catch (MergingException e) {
      LOG.warn(e);
    }

    ResourceMerger resourceMerger = new ResourceMerger(0);
    resourceMerger.addDataSet(resourceSet);
    repository.getItems().update(resourceMerger);

    repository.loadPublicResources();
    if (usePersistentCache) {
      // It would be nice to create persistent cache asynchronously, but unfortunately
      // this is not possible because Xerces DOM implementation is not thread-safe.
      // See https://xerces.apache.org/xerces2-j/faq-dom.html#faq-1.
      repository.createPersistentCache();
    }
    return repository;
  }

  @Override
  @NonNull
  public Collection<ResourceItem> getPublicResourcesOfType(@NonNull ResourceType type) {
    List<ResourceItem> resourceItems = myPublicResources.get(type);
    return resourceItems == null ? Collections.emptyList() : resourceItems;
  }

  @VisibleForTesting
  @NotNull
  static File getCacheFile(File resourceDir, boolean withLocaleResources) {
    String dirHash = Hashing.md5().hashUnencodedChars(resourceDir.getAbsolutePath()).toString();
    String filename = String.format("%s%s.bin", dirHash, withLocaleResources ? "_L" : "");
    return new File(new File(PathManager.getSystemPath(), CACHE_DIRECTORY), filename);
  }

  @VisibleForTesting
  boolean isLoadedFromCache() {
    return myLoadedFromCache;
  }

  /**
   * Populates {@link #myPublicResources} by parsing res/values/public.xml.
   */
  private void loadPublicResources() {
    File valuesFolder = new File(getResourceDirectory(), SdkConstants.FD_RES_VALUES);
    File publicXmlFile = new File(valuesFolder, "public.xml");

    if (publicXmlFile.exists()) {
      try (InputStream stream = new BufferedInputStream(new FileInputStream(publicXmlFile))) {
        KXmlParser parser = new KXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(stream, StandardCharsets.UTF_8.name());

        ResourceType lastType = null;
        String lastTypeName = "";
        while (true) {
          int event = parser.next();
          if (event == XmlPullParser.START_TAG) {
            // As of API 15 there are a number of "java-symbol" entries here.
            if (!parser.getName().equals(SdkConstants.TAG_PUBLIC)) {
              continue;
            }

            String name = null;
            String typeName = null;
            for (int i = 0, n = parser.getAttributeCount(); i < n; i++) {
              String attribute = parser.getAttributeName(i);

              if (attribute.equals(SdkConstants.ATTR_NAME)) {
                name = parser.getAttributeValue(i);
                if (typeName != null) {
                  // Skip id attribute processing.
                  break;
                }
              }
              else if (attribute.equals(SdkConstants.ATTR_TYPE)) {
                typeName = parser.getAttributeValue(i);
              }
            }

            if (name != null && typeName != null) {
              ResourceType type;
              if (typeName.equals(lastTypeName)) {
                type = lastType;
              }
              else {
                type = ResourceType.getEnum(typeName);
                lastType = type;
                lastTypeName = typeName;
              }

              if (type != null) {
                List<ResourceItem> matchingResources = getResourceItems(ResourceNamespace.ANDROID, type, name);
                // Some entries in public.xml point to attributes defined attrs_manifest.xml and therefore
                // don't match any resources.
                if (!matchingResources.isEmpty()) {
                  List<ResourceItem> publicList = myPublicResources.get(type);
                  if (publicList == null) {
                    publicList = new ArrayList<>(getMap(type, false).size());
                    myPublicResources.put(type, publicList);
                  }

                  publicList.addAll(matchingResources);
                }
              }
              else {
                LOG.error("Public resource declaration \"" + name + "\" of type " + typeName + " points to unknown resource type.");
              }
            }
          }
          else if (event == XmlPullParser.END_DOCUMENT) {
            break;
          }
        }
      } catch (Exception e) {
        LOG.error("Can't read and parse public attribute list " + publicXmlFile.getPath(), e);
      }
    }

    // Put unmodifiable list for all resource types in the public resource map.
    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> list = myPublicResources.get(type);
      if (list == null) {
        list = Collections.emptyList();
      }
      else {
        list = ImmutableList.copyOf(list); // Make immutable and free unused memory.
      }

      // put the new list in the map
      myPublicResources.put(type, list);
    }
  }

  @Override
  @NotNull
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    if (!NAMESPACE.equals(namespace)) {
      if (create) {
        throw new IllegalArgumentException("Invalid namespace: " + namespace);
      }
      return ImmutableListMultimap.of();
    }
    return getMap(type, create);
  }

  @NotNull
  private ListMultimap<String, ResourceItem> getMap(@NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> map = super.getMap(NAMESPACE, type, create);
    return map == null ? ImmutableListMultimap.of() : map;
  }

  @Override
  @NotNull
  public Set<ResourceType> getAvailableResourceTypes() {
    return EnumSet.copyOf(getMapByType().keySet());
  }

  /**
   * Returns true if the resource repository includes locale-specific resources, otherwise false.
   */
  public boolean isWithLocaleResources() {
    return myWithLocaleResources;
  }

  /**
   * Loads the framework resource repository from a binary cache file on disk.
   *
   * @return true if the repository was loaded from the cache, or false if the cache does not
   *     exist or is out of date
   * @see #createPersistentCache()
   */
  private boolean loadFromPersistentCache() {
    File cacheFile = getCacheFile();
    if (!cacheFile.exists()) {
      return false; // Cache file does not exist.
    }

    try (CacheInputStream in = new CacheInputStream(cacheFile)) {
      if (!in.readUTF().equals(getResourceDirectory().getAbsolutePath())) {
        return false; // The cache is for a different resource directory.
      }
      if (!in.readUTF().equals(getAndroidPluginVersion())) {
        // The cache was created by a different version of the Android plugin.
        // For safety we don't assume any compatibility of cache file format between
        // versions of the Android plugin.
        return false;
      }

      // Read folder configurations.
      int numFolderConfigurations = in.readUnsignedShort();
      FolderConfiguration[] folderConfigurations = new FolderConfiguration[numFolderConfigurations];
      for (int i = 0; i < numFolderConfigurations; i++) {
        String qualifiers = in.readUTF();
        FolderConfiguration folderConfig = FolderConfiguration.getConfigForQualifierString(qualifiers);
        if (folderConfig == null) {
          throw new StreamCorruptedException("Invalid folder qualifiers: " + qualifiers);
        }
        folderConfigurations[i] = folderConfig;
      }

      // Read resource files containing multiple resources.
      int numFiles = in.readUnsignedShort();
      ResourceFile[] resourceFiles = new ResourceFile[numFiles];
      for (int i = 0; i < numFiles; i++) {
        String path = in.readUTF();
        int folderConfigurationIndex = in.readUnsignedShort();
        FolderConfiguration folderConfig = folderConfigurations[folderConfigurationIndex];
        resourceFiles[i] = new ResourceFile(new File(path), Collections.emptyList(), folderConfig.getQualifierString(), folderConfig);
      }

      // Read resource items.
      int numTypes = in.readUnsignedByte();
      for (int i = 0; i < numTypes; i++) {
        ResourceType resourceType = in.readResourceType();
        ListMultimap<String, ResourceItem> map = getMap(resourceType, true);
        int numResources = in.readUnsignedShort();
        for (int j = 0; j < numResources; j++) {
          String resourceName = in.readUTF();
          int n = in.readUnsignedShort();
          for (int k = 0; k < n; k++) {
            Node node = in.readNode();
            ResourceItemType itemType = ResourceItemType.values()[in.readUnsignedByte()];
            ResourceItem item = null;
            switch (itemType) {
              case REGULAR: {
                item = new ResourceItem(resourceName, NAMESPACE, resourceType, node, null);
                int fileIndex = in.readUnsignedShort();
                ResourceFile resourceFile = resourceFiles[fileIndex];
                resourceFile.addItem(item);
                break;
              }
              case FILE: {
                item = new ResourceItem(resourceName, NAMESPACE, resourceType, node, null);
                int folderConfigurationIndex = in.readUnsignedShort();
                FolderConfiguration folderConfig = folderConfigurations[folderConfigurationIndex];
                String path = in.readUTF();
                new ResourceFile(new File(path), item, folderConfig.getQualifierString(), folderConfig);
                break;
              }
              case MERGED: {
                int folderConfigurationIndex = in.readUnsignedShort();
                FolderConfiguration folderConfig = folderConfigurations[folderConfigurationIndex];
                item = new MergedResourceItem(resourceName, NAMESPACE, resourceType, folderConfig.getQualifierString(), node, null);
                break;
              }
            }

            if (item != null) {
              map.put(resourceName, item);
            }
          }
        }
      }

      // Read public resources.
      int numPublic = in.readUnsignedByte();
      for (int i = 0; i < numPublic; i++) {
        ResourceType resourceType = in.readResourceType();
        ListMultimap<String, ResourceItem> map = getMap(resourceType, false);
        int m = in.readUnsignedShort();
        for (int j = 0; j < m; j++) {
          String resourceName = in.readUTF();
          List<ResourceItem> items = map.get(resourceName);
          if (items == null) {
            throw new StreamCorruptedException("Unresolved public resource reference, type: " + resourceType.getName()
                                               + ", name: " + resourceName);
          }
          List<ResourceItem> publicItems = myPublicResources.get(resourceType);
          if (publicItems == null) {
            publicItems = new ArrayList<>(items.size());
            myPublicResources.put(resourceType, publicItems);
          }
          publicItems.addAll(items);
        }
      }

      myLoadedFromCache = true;
    }
    catch (VersionMismatchException e) {
      return false; // Cache file format does not match.
    }
    catch (Throwable e) {
      LOG.warn("Unable to load from cache file " + cacheFile.getAbsolutePath(), e);
      return false;
    } finally {
      if (!myLoadedFromCache) {
        getMapByType().clear();  // Remove partially loaded data.
      }
    }

    return true;
  }

  /**
   * Creates a persistent cache file with the following format:
   * <ol>
   *   <li>Header (see below)</li>
   *   <li>Absolute path of the resource directory (UTF-8 string)</li>
   *   <li>Version of the Android plugin (UTF-8 string)</li>
   *   <li>Number of folder configurations (unsigned short)</li>
   *   <li>Qualifier strings of folder configurations (UTF-8 strings)</li>
   *   <li>Number of multi-resource files (unsigned short)</li>
   *   <li>Multi-resource file entries (see below)</li>
   *   <li>Number of resource group entries (unsigned byte)</li>
   *   <li>Resource group entries (see below)</li>
   *   <li>Number of public resource group entries (unsigned byte)</li>
   *   <li>Public resource group entries (see below)</li>
   * </ol>
   *
   * The header contains:
   * <ol>
   *   <li>The {@linkplain #CACHE_FILE_HEADER} string (one byte per character)</li>
   *   <li>Space (one byte)</li>
   *   <li>The {@linkplain #CACHE_FILE_FORMAT_VERSION} string (one byte per character)</li>
   *   <li>Space (one byte)</li>
   * </ol>
   *
   * A multi-resource file entry contains:
   * <ol>
   *   <li>File path (UTF-8 string)</li>
   *   <li>Index of the corresponding folder configuration (unsigned short)</li>
   * </ol>
   *
   * A resource group entry contains:
   * <ol>
   *   <li>{@linkplain ResourceType} represented by its ordinal (unsigned byte)</li>
   *   <li>Number of resource subgroups (unsigned short)</li>
   *   <li>Resource subgroup entries (see below)</li>
   * </ol>
   *
   * A resource subgroup entry contains:
   * <ol>
   *   <li>Resource name (UTF-8 string)</li>
   *   <li>Number of resource items (unsigned short)</li>
   *   <li>Resource item entries (see below)</li>
   * </ol>
   *
   * A resource item entry contains:
   * <ol>
   *   <li>The XML node entry associated with the resource (see below), or a zero byte if the resource
   *   does not have an associated XML node</li>
   *   <li>The type of the entry represented by the ordinal of {@linkplain ResourceItemType} (unsigned byte)</li>
   *   <li>If the type of the entry is {@linkplain ResourceItemType#REGULAR}, the index of the corresponding
   *       multi-resource file (unsigned short)</li>
   *   <li>If the type of the entry is not {@linkplain ResourceItemType#REGULAR}, the index of the corresponding
   *       folder configuration (unsigned short)</li>
   *   <li>If the type of the entry is {@linkplain ResourceItemType#FILE}, the path of the file (UTF-8 string)</li>
   * </ol>
   *
   * A public resource group entry contains:
   * <ol>
   *   <li>{@linkplain ResourceType} represented by its ordinal (unsigned byte)</li>
   *   <li>Number of public resources corresponding to the type above (unsigned short)</li>
   *   <li>Resource names (UTF-8 strings)</li>
   * </ol>
   *
   * An XML node entry contains:
   * <ol>
   *   <li>The type of the node, Node.ELEMENT_NODE or Node.TEXT_NODE (unsigned byte)</li>
   *   <li>If the node is an {@link Element}, the XML element entry (see below)</li>
   *   <li>If the node is a {@link Text}, the value of the node (UTF-8 string)</li>
   * </ol>
   *
   * An XML element entry contains:
   * <ol>
   *   <li>The name of the node (UTF-8 string)</li>
   *   <li>The number of attributes (unsigned byte)</li>
   *   <li>XML attribute entries, one for each attribute (see below)</li>
   *   <li>The number of child nodes (unsigned byte)</li>
   *   <li>XML node entries, one for each child</li>
   * </ol>
   *
   * An XML attribute entry contains:
   * <ol>
   *   <li>The name of the attribute (UTF-8 string)</li>
   *   <li>The value of the attribute (UTF-8 string)</li>
   * </ol>
   */
  private void createPersistentCache() {
    File cacheFile = getCacheFile();
    //noinspection ResultOfMethodCallIgnored
    cacheFile.delete();

    // Write to a temporary file first, then rename to to the final name.
    File tempFile;
    try {
      tempFile = FileUtilRt.createTempFile(cacheFile.getParentFile(), cacheFile.getName(), ".tmp");
    } catch (IOException e) {
      LOG.error("Unable to create a temporary file in " + cacheFile.getParentFile().getAbsolutePath(), e);
      return;
    }

    try (CacheOutputStream out = new CacheOutputStream(tempFile)) {
      out.writeUTF(getResourceDirectory().getAbsolutePath());

      // Write version of the Android plugin.
      out.writeUTF(getAndroidPluginVersion());

      // Extract all referenced folder configurations and multi-resource files.
      List<FolderConfiguration> folderConfigurations = new ArrayList<>();
      ObjectIntHashMap<FolderConfiguration> folderConfigurationIndexes = new ObjectIntHashMap<>();
      List<ResourceFile> multiResourceFiles = new ArrayList<>();
      ObjectIntHashMap<File> multiResourceFileIndexes = new ObjectIntHashMap<>();
      Map<ResourceType, ListMultimap<String, ResourceItem>> mapByType = getMapByType();
      for (ListMultimap<String, ResourceItem> map : mapByType.values()) {
        for (ResourceItem resourceItem : map.values()) {
          FolderConfiguration folderConfiguration = resourceItem.getConfiguration();
          if (!folderConfigurationIndexes.containsKey(folderConfiguration)) {
            folderConfigurationIndexes.put(folderConfiguration, folderConfigurations.size());
            folderConfigurations.add(folderConfiguration);
          }

          if (resourceItem.getSourceType() != DataFile.FileType.SINGLE_FILE) {
            ResourceFile resourceFile = resourceItem.getSource();
            if (resourceFile != null) {
              File file = resourceFile.getFile();
              if (!multiResourceFileIndexes.containsKey(file)) {
                multiResourceFileIndexes.put(file, multiResourceFiles.size());
                multiResourceFiles.add(resourceFile);
              }
            }
          }
        }
      }

      // Write qualifier strings of folder configurations.
      if (folderConfigurations.size() > 0xFFFF) {
        throw new IOException("Too many folder configurations: " + folderConfigurations.size());
      }
      out.writeShort(folderConfigurations.size());
      for (FolderConfiguration config : folderConfigurations) {
        out.writeUTF(config.getQualifierString());
      }

      // Write paths of the files containing multiple resources.
      if (multiResourceFiles.size() > 0xFFFF) {
        throw new IOException("Too many multi-resource files: " + multiResourceFiles.size());
      }
      out.writeShort(multiResourceFiles.size());
      for (ResourceFile resourceFile : multiResourceFiles) {
        out.writeUTF(resourceFile.getFile().getPath());
        int folderConfigurationIndex = folderConfigurationIndexes.get(resourceFile.getFolderConfiguration());
        out.writeShort(folderConfigurationIndex);
      }

      // Write resource items.
      Set<Map.Entry<ResourceType, ListMultimap<String, ResourceItem>>> typeEntries = mapByType.entrySet();
      int numNonEmpty = 0;
      for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> typeEntry : typeEntries) {
        if (!typeEntry.getValue().isEmpty()) {
          numNonEmpty++;
        }
      }

      out.writeByte(numNonEmpty);
      for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> typeEntry : typeEntries) {
        ListMultimap<String, ResourceItem> multimap = typeEntry.getValue();
        if (!multimap.isEmpty()) {
          ResourceType resourceType = typeEntry.getKey();
          out.writeResourceType(resourceType);
          Map<String, Collection<ResourceItem>> resources = multimap.asMap();
          if (resources.size() > 0xFFFF) {
            throw new IOException("Too many resources of type " + resourceType.getName() + ": " + resources.size());
          }

          out.writeShort(resources.size());
          for (Map.Entry<String, Collection<ResourceItem>> itemEntry : resources.entrySet()) {
            String resourceName = itemEntry.getKey();
            Collection<ResourceItem> resourceItems = itemEntry.getValue();
            out.writeUTF(resourceName);
            if (resources.size() > 0xFFFF) {
              throw new IOException("Too many resources items: " + resourceItems.size());
            }

            out.writeShort(resourceItems.size());
            for (ResourceItem resourceItem : resourceItems) {
              out.writeNode(resourceItem.getValue());
              ResourceFile resourceFile = resourceItem.getSource();
              ResourceItemType itemType;
              if (resourceItem instanceof MergedResourceItem) {
                itemType = ResourceItemType.MERGED;
              } else if (resourceItem.getSourceType() == DataFile.FileType.SINGLE_FILE) {
                itemType = ResourceItemType.FILE;
              } else {
                itemType = ResourceItemType.REGULAR;
              }
              out.writeByte(itemType.ordinal());
              if (itemType == ResourceItemType.REGULAR) {
                assert resourceFile != null;
                out.writeShort(multiResourceFileIndexes.get(resourceFile.getFile()));
              } else {
                int folderConfigurationIndex = folderConfigurationIndexes.get(resourceItem.getConfiguration());
                out.writeShort(folderConfigurationIndex);
                if (itemType == ResourceItemType.FILE) {
                  assert resourceFile != null;
                  out.writeUTF(resourceFile.getFile().getPath());
                }
              }
            }
          }
        }
      }

      // Write public resources.
      numNonEmpty = 0;
      for (Map.Entry<ResourceType, List<ResourceItem>> typeEntry : myPublicResources.entrySet()) {
        if (!typeEntry.getValue().isEmpty()) {
          numNonEmpty++;
        }
      }
      out.writeByte(numNonEmpty);
      for (Map.Entry<ResourceType, List<ResourceItem>> entry : myPublicResources.entrySet()) {
        List<ResourceItem> resourceItems = entry.getValue();
        if (!resourceItems.isEmpty()) {
          ResourceType resourceType = entry.getKey();
          out.writeResourceType(resourceType);
          Set<String> uniqueNames = newLinkedHashSetWithExpectedSize(resourceItems.size());
          for (ResourceItem item : resourceItems) {
            uniqueNames.add(item.getName());
          }
          if (uniqueNames.size() > 0xFFFF) {
            throw new IOException("Too many public resources of type " + resourceType.getName() + ": " + uniqueNames.size());
          }
          out.writeShort(uniqueNames.size());
          for (String name : uniqueNames) {
            out.writeUTF(name);
          }
        }
      }
    }
    catch (Throwable e) {
      LOG.error("Unable to create cache file " + tempFile.getAbsolutePath(), e);
      //noinspection ResultOfMethodCallIgnored
      tempFile.delete();
      return;
    }

    try {
      Files.move(tempFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      LOG.error("Unable to create cache file " + cacheFile.getAbsolutePath(), e);
      //noinspection ResultOfMethodCallIgnored
      tempFile.delete();
    }
  }

  @NotNull
  private Map<ResourceType, ListMultimap<String, ResourceItem>> getMapByType() {
    return getFullTable().row(NAMESPACE);
  }

  @NotNull
  private File getCacheFile() {
    return getCacheFile(getResourceDirectory(), myWithLocaleResources);
  }

  @NotNull
  private static String getAndroidPluginVersion() {
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(ANDROID_PLUGIN_ID));
    if (plugin == null) {
      return "unknown";
    }
    return plugin.getVersion();
  }

  private static class CacheOutputStream extends ObjectOutputStream {
    CacheOutputStream(@NotNull File file) throws IOException {
      super(new BufferedOutputStream(new FileOutputStream(file)));
    }

    @Override
    protected void writeStreamHeader() throws IOException {
      for (int i = 0; i < CACHE_FILE_HEADER.length(); i++) {
        writeByte(CACHE_FILE_HEADER.charAt(i));
      }
      writeByte(' ');
      for (int i = 0; i < CACHE_FILE_FORMAT_VERSION.length(); i++) {
        writeByte(CACHE_FILE_FORMAT_VERSION.charAt(i));
      }
      writeByte(' ');
    }

    void writeResourceType(@NotNull ResourceType type) throws IOException {
      writeByte(type.ordinal());
    }

    void writeAttribute(@NotNull Attr attribute) throws IOException {
      writeUTF(attribute.getName());
      writeUTF(attribute.getValue());
    }

    void writeNode(@Nullable Node node) throws IOException {
      if (node == null) {
        writeByte(0);
      } else {
        short nodeType = node.getNodeType();
        writeByte(nodeType);
        if (nodeType == Node.ELEMENT_NODE) {
          writeUTF(node.getNodeName());
          NamedNodeMap attributes = node.getAttributes();
          int numAttributes = attributes.getLength();
          if (numAttributes > 0xFF) {
            throw new IOException("XML node " + node.getNodeName() + " has too many attributes: " + numAttributes);
          }
          writeByte(numAttributes);
          for (int i = 0; i < numAttributes; i++) {
            writeAttribute((Attr)attributes.item(i));
          }
          NodeList children = node.getChildNodes();
          int numChildren = children.getLength();
          if (numChildren > 0xFFFF) {
            throw new IOException("XML node " + node.getNodeName() + " has too many children: " + numChildren);
          }
          int numSignificantChildren = numChildren;
          for (int i = 0; i < numChildren; i++) {
            if (children.item(i).getNodeType() == Node.COMMENT_NODE) {
              numSignificantChildren--;
            }
          }
          writeShort(numSignificantChildren);
          for (int i = 0; i < numChildren; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.COMMENT_NODE) {
              writeNode(child);
            }
          }
        } else if (nodeType == Node.TEXT_NODE) {
          writeUTF(node.getNodeValue());
        } else {
          throw new RuntimeException("Unsupported XML node type: " + nodeType);
        }
      }
    }
  }

  private static class CacheInputStream extends ObjectInputStream {
    CacheInputStream(@NotNull File file) throws IOException {
      super(new BufferedInputStream(new FileInputStream(file)));
    }

    @Override
    protected void readStreamHeader() throws IOException {
      for (int i = 0; i < CACHE_FILE_HEADER.length(); i++) {
        if (readUnsignedByte() != CACHE_FILE_HEADER.charAt(i)) {
          throw new StreamCorruptedException();
        }
      }
      if (readUnsignedByte() != ' ') {
        throw new StreamCorruptedException();
      }
      for (int i = 0; i < CACHE_FILE_FORMAT_VERSION.length(); i++) {
        if (readUnsignedByte() != CACHE_FILE_FORMAT_VERSION.charAt(i)) {
          throw new VersionMismatchException();
        }
      }
      if (readUnsignedByte() != ' ') {
        throw new VersionMismatchException();
      }
    }

    @NotNull
    ResourceType readResourceType() throws IOException {
      int ordinal = readUnsignedByte();
      try {
        return ResourceType.values()[ordinal];
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new StreamCorruptedException("Invalid resource type reference: " + ordinal);
      }
    }

    @NotNull
    Attr readAttribute() throws IOException {
      String name = readUTF();
      String value = readUTF();
      return new AttrImpl(name, value);
    }

    @Nullable
    Node readNode() throws IOException {
      int nodeType = readUnsignedByte();
      switch (nodeType) {
        case 0:
          return null;

        case Node.ELEMENT_NODE:
          String name = readUTF();
          int numAttributes = readUnsignedByte();
          ArrayList<Node> attributes = new ArrayList<>(numAttributes);
          for (int i = 0; i < numAttributes; i++) {
            attributes.add(readAttribute());
          }
          NamedNodeMap attributeMap = new NamedNodeMapImpl(attributes);
          int numChildren = readUnsignedShort();
          List<Node> children = numChildren == 0 ? Collections.emptyList() : new ArrayList<>(numChildren);
          for (int i = 0; i < numChildren; i++) {
            Node child = readNode();
            children.add(child);
          }
          return new ElementImpl(name, attributeMap, children);

        case Node.TEXT_NODE:
          String text = readUTF();
          return new TextImpl(text);

        default:
          throw new RuntimeException("Unexpected node type: " + nodeType);
      }

    }
  }

  private static class VersionMismatchException extends IOException {
  }

  private static class FrameworkResourceSet extends ResourceSet {
    private final boolean myWithLocaleResources;

    FrameworkResourceSet(@NotNull File resourceFolder, boolean withLocaleResources) {
      super("AndroidFramework", NAMESPACE, null, false);
      myWithLocaleResources = withLocaleResources;
      addSource(resourceFolder);
      setTrackSourcePositions(false);
      setCheckDuplicates(false);
      setAllowUnspecifiedAttrFormat(true);
    }

    @Override
    public boolean isIgnored(@NotNull File file) {
      if (super.isIgnored(file)) {
        return true;
      }

      String fileName = file.getName();
      // TODO: Restrict the following checks to folders only.
      if (fileName.startsWith("values-mcc") || fileName.startsWith("raw-")) {
        return true; // Mobile country codes and raw resources are not used by LayoutLib.
      }

      // Skip locale-specific folders if myWithLocaleResources is false.
      if (!myWithLocaleResources && fileName.startsWith("values-")) {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(fileName);
        if (config == null || config.getLocaleQualifier() != null) {
          return true;
        }
      }

      // Skip files that don't contain resources.
      if (fileName.equals("attrs_manifest.xml") || fileName.equals("public.xml") || fileName.equals("symbols.xml")) {
        return true;
      }

      return false;
    }
  }

  private static final class ElementImpl extends NamedNodeImpl implements Element, NodeList {
    @NotNull private final NamedNodeMap myAttributeMap;
    @NotNull private final List<Node> myChildren;

    private ElementImpl(@NotNull String name, @NotNull NamedNodeMap attributeMap, @NotNull List<Node> children) {
      super(name);
      myAttributeMap = attributeMap;
      myChildren = children;
    }

    @Override
    public short getNodeType() {
      return ELEMENT_NODE;
    }

    @Override
    public String getTagName() {
      return getNodeName();
    }

    @Override
    public NamedNodeMap getAttributes() {
      return myAttributeMap;
    }

    @Override
    public String getAttribute(String name) {
      return myAttributeMap.getNamedItem(name).getNodeValue();
    }

    @Override
    public Attr getAttributeNode(String name) {
      return (Attr)myAttributeMap.getNamedItem(name);
    }

    @Override
    public String getAttributeNS(String namespaceUri, String localName) throws DOMException {
      return myAttributeMap.getNamedItemNS(namespaceUri, localName).getNodeValue();
    }

    @Override
    public Attr getAttributeNodeNS(String namespaceUri, String localName) throws DOMException {
      return (Attr)myAttributeMap.getNamedItemNS(namespaceUri, localName);
    }

    @Override
    public boolean hasAttribute(String name) {
      return myAttributeMap.getNamedItem(name) != null;
    }

    @Override
    public boolean hasAttributeNS(String namespaceUri, String localName) throws DOMException {
      return myAttributeMap.getNamedItemNS(namespaceUri, localName) != null;
    }

    @Override
    public NodeList getChildNodes() {
      return this;
    }

    @Override
    public void setAttribute(String name, String value) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void removeAttribute(String name) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Attr setAttributeNode(Attr newAttr) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public NodeList getElementsByTagName(String name) {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setAttributeNS(String namespaceUri, String qualifiedName, String value) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void removeAttributeNS(String namespaceUri, String localName) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public NodeList getElementsByTagNameNS(String namespaceUri, String localName) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setIdAttribute(String name, boolean isId) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setIdAttributeNS(String namespaceUri, String localName, boolean isId) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node item(int index) {
      return index < myChildren.size() ? myChildren.get(index) : null;
    }

    @Override
    public int getLength() {
      return myChildren.size();
    }
  }

  private static class NamedNodeMapImpl implements NamedNodeMap {
    @NotNull private final List<Node> myNodes;

    private NamedNodeMapImpl(@NotNull List<Node> nodes) {
      myNodes = nodes;
    }

    @Override
    public Node getNamedItem(String name) {
      for (int i = 0; i < myNodes.size(); i++) {
        Node node = myNodes.get(i);
        if (node.getNodeName().equals(name)) {
          return node;
        }
      }
      return null;
    }

    @Override
    public Node item(int index) {
      return myNodes.get(index);
    }

    @Override
    public int getLength() {
      return myNodes.size();
    }

    @Override
    public Node getNamedItemNS(String namespaceUri, String localName) throws DOMException {
      for (int i = 0; i < myNodes.size(); i++) {
        Node node = myNodes.get(i);
        String name = node.getNodeName();
        int colonPos = name.indexOf(':');
        int offset;
        if (colonPos < 0) {
          if (namespaceUri != null) {
            continue;
          }
          offset = 0;
        } else {
          if (namespaceUri == null) {
            continue;
          }
          offset = colonPos + 1;
        }
        if (name.length() == offset + localName.length()
            && name.regionMatches(offset, localName, 0, localName.length())) {
          return node;
        }
      }
      return null;
    }

    @Override
    public Node setNamedItem(Node arg) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node removeNamedItem(String name) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node setNamedItemNS(Node arg) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node removeNamedItemNS(String namespaceUri, String localName) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }
  }

  private static final class AttrImpl extends NamedNodeImpl implements Attr {
    @NotNull private final String myValue;

    private AttrImpl(@NotNull String name, @NotNull String value) {
      super(name);
      myValue = value;
    }

    @Override
    public short getNodeType() {
      return ATTRIBUTE_NODE;
    }

    @Override
    public String getName() {
      return getNodeName();
    }

    @Override
    @NotNull
    public String getValue() {
      return myValue;
    }

    @Override
    @NotNull
    public String getNodeValue() {
      return myValue;
    }

    @Override
    public void setValue(String value) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public boolean getSpecified() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Element getOwnerElement() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public TypeInfo getSchemaTypeInfo() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public boolean isId() {
      throw createAndLogUnsupportedOperationException();
    }
  }

  private static final class TextImpl extends NodeImpl implements Text {
    @NotNull private final String myText;

    private TextImpl(@NotNull String text) {
      myText = text;
    }

    @Override
    public short getNodeType() {
      return TEXT_NODE;
    }

    @Override
    public String getNodeValue() throws DOMException {
      return myText;
    }

    @Override
    public String getWholeText() {
      return myText;
    }

    @Override
    public int getLength() {
      return myText.length();
    }

    @Override
    public String substringData(int offset, int count) throws DOMException {
      return myText.substring(offset, offset + count);
    }

    @Override
    public Text splitText(int offset) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public boolean isElementContentWhitespace() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Text replaceWholeText(String content) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public String getData() throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setData(String data) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void appendData(String arg) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void insertData(int offset, String arg) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void deleteData(int offset, int count) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void replaceData(int offset, int count, String arg) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }
  }

  private static abstract class NamedNodeImpl extends NodeImpl {
    @NotNull private final String myName;

    private NamedNodeImpl(@NotNull String name) {
      myName = name;
    }

    @Override
    public final String getNodeName() {
      return myName;
    }

    @Override
    public String getLocalName() {
      int colonPos = myName.lastIndexOf(':');
      if (colonPos < 0) {
        return myName;
      }
      return myName.substring(colonPos + 1);
    }

    @Override
    public final String getPrefix() {
      int colonPos = myName.indexOf(':');
      if (colonPos < 0) {
        return null;
      }
      return myName.substring(0, colonPos);
    }

    @Override
    public final String getNamespaceURI() {
      int colonPos = myName.indexOf(':');
      if (colonPos < 0) {
        return null;
      }
      // Only well-known namespaces are supported.
      for (int i = 0; i < WELL_KNOWN_NAMESPACES.length; i += 2) {
        String prefix = WELL_KNOWN_NAMESPACES[i];
        if (prefix.length() == colonPos && myName.startsWith(prefix)) {
          return WELL_KNOWN_NAMESPACES[i + 1];
        }
      }
      throw new IllegalStateException("Unknown namespace prefix: \"" + myName.substring(0, colonPos) + "\"");
    }
  }

  private static abstract class NodeImpl implements Node {
    static final NodeList EMPTY_NODE_LIST = new NodeList() {
      @Override
      public Node item(int index) {
        return null;
      }

      @Override
      public int getLength() {
        return 0;
      }
    };

    @Override
    public String getNodeName() {
      return null;
    }

    @Override
    public NamedNodeMap getAttributes() {
      return null;
    }

    @Override
    public boolean hasAttributes() {
      return false;
    }

    @Override
    public NodeList getChildNodes() {
      return EMPTY_NODE_LIST;
    }

    @Override
    public boolean hasChildNodes() {
      return getChildNodes().getLength() != 0;
    }

    @Override
    public String getLocalName() {
      return null;
    }

    @Override
    public String getPrefix() {
      return null;
    }

    @Override
    public String getNamespaceURI() {
      return null;
    }

    @Override
    public String getNodeValue() throws DOMException {
      return null;
    }

    @Override
    public void setNodeValue(String nodeValue) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node getParentNode() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node getFirstChild() {
      NodeList children = getChildNodes();
      return children.getLength() != 0 ? children.item(0) : null;
    }

    @Override
    public Node getLastChild() {
      NodeList children = getChildNodes();
      int length = children.getLength();
      return length != 0 ? children.item(length - 1) : null;
    }

    @Override
    public Node getPreviousSibling() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node getNextSibling() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Document getOwnerDocument() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node insertBefore(Node newChild, Node refChild) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node removeChild(Node oldChild) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node appendChild(Node newChild) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Node cloneNode(boolean deep) {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void normalize() {
    }

    @Override
    public boolean isSupported(String feature, String version) {
      return false;
    }

    @Override
    public void setPrefix(String prefix) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public String getBaseURI() {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public short compareDocumentPosition(Node other) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public String getTextContent() throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public void setTextContent(String textContent) throws DOMException {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public boolean isSameNode(Node other) {
      return this == other;
    }

    @Override
    public String lookupPrefix(String namespaceUri) {
      // Only well-known namespaces are supported.
      for (int i = 1; i < WELL_KNOWN_NAMESPACES.length; i += 2) {
        if (WELL_KNOWN_NAMESPACES[i].equals(namespaceUri)) {
          return WELL_KNOWN_NAMESPACES[i - 1];
        }
      }
      throw new IllegalStateException("Unknown namespace URI: \"" + namespaceUri + "\"");
    }

    @Override
    public boolean isDefaultNamespace(String namespaceUri) {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public String lookupNamespaceURI(String prefix) {
      // Only well-known namespaces are supported.
      for (int i = 0; i < WELL_KNOWN_NAMESPACES.length; i += 2) {
        if (WELL_KNOWN_NAMESPACES[i].equals(prefix)) {
          return WELL_KNOWN_NAMESPACES[i + 1];
        }
      }
      throw new IllegalStateException("Unknown namespace prefix: \"" + prefix + "\"");
    }

    @Override
    public boolean isEqualNode(Node other) {
      return this == other;
    }

    @Override
    public Object getFeature(String feature, String version) {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Object setUserData(String key, Object data, UserDataHandler handler) {
      throw createAndLogUnsupportedOperationException();
    }

    @Override
    public Object getUserData(String key) {
      throw createAndLogUnsupportedOperationException();
    }
  }

  @NotNull
  private static UnsupportedOperationException createAndLogUnsupportedOperationException() {
    UnsupportedOperationException exception = new UnsupportedOperationException();
    LOG.error("Unsupported operation in FrameworkResourceRepository", exception);
    return exception;
  }

  private enum ResourceItemType {
    /** Resource associated with a {@linkplain DataFile.FileType#XML_VALUES} file. */
    REGULAR,
    /** Resource associated with a {@linkplain DataFile.FileType#SINGLE_FILE} file. */
    FILE,
    /** Merged resource. See {@linkplain MergedResourceItem}. */
    MERGED
  }
}
