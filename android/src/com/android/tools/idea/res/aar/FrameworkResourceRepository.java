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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

/**
 * Repository of resources of the Android framework.
 *
 * <p>For performance the repository, when possible, is loaded from a binary cache file located
 * under the directory returned by the {@link PathManager#getSystemPath()} method.
 * Loading from a cache file is 7-8 times faster than reading XML files.
 *
 * <p>For safety we don't assume any compatibility of cache file format between different versions
 * of the Android plugin. For the built-in framework resources used by LayoutLib this also guarantees
 * freshness of the cache when the Android plugin is updated.
 *
 * @see ResourceRepositoryManager#getFrameworkResources(boolean)
 */
public final class FrameworkResourceRepository extends AarSourceResourceRepository {
  private static final ResourceNamespace ANDROID_NAMESPACE = ResourceNamespace.ANDROID;
  private static final String CACHE_DIRECTORY = "caches/framework_resources";
  private static final byte[] CACHE_FILE_HEADER = "Framework resource cache".getBytes(StandardCharsets.UTF_8);
  private static final String CACHE_FILE_FORMAT_VERSION = "5";
  private static final String ANDROID_PLUGIN_ID = "org.jetbrains.android";

  private static final Logger LOG = Logger.getInstance(FrameworkResourceRepository.class);

  private final boolean myWithLocaleResources;
  private Future myCacheCreatedFuture;

  private FrameworkResourceRepository(@NotNull Path resFolder, boolean withLocaleResources) {
    super(resFolder, ANDROID_NAMESPACE, null, null);
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
    LOG.debug("Creating FrameworkResourceRepository for " + resFolder);

    FrameworkResourceRepository repository = new FrameworkResourceRepository(resFolder.toPath(), withLocaleResources);
    // Try to load from file cache first. Loading from cache is significantly faster than reading resource files.
    if (usePersistentCache && repository.loadFromPersistentCache()) {
      return repository;
    }

    LOG.debug("Loading FrameworkResourceRepository from sources in " + resFolder);
    repository.load();

    if (usePersistentCache) {
      repository.createPersistentCacheAsynchronously();
    }
    return repository;
  }

  private void load() {
    Loader loader = new MyLoader();
    loader.load(ImmutableList.of(myResourceDirectory), true);
  }

  private void createPersistentCacheAsynchronously() {
    myCacheCreatedFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> createPersistentCache());
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ANDROID_NAMESPACE.getPackageName();
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Android framework";
  }

  @VisibleForTesting
  @NotNull
  static Path getCacheFile(@NotNull Path resourceDir, boolean withLocaleResources) {
    String dirHash = Hashing.md5().hashUnencodedChars(resourceDir.toString()).toString();
    String filename = String.format("%s%s.bin", dirHash, withLocaleResources ? "_L" : "");
    return Paths.get(PathManager.getSystemPath(), CACHE_DIRECTORY, filename);
  }

  /**
   * Waits until the asynchronous creation of the persistent cache finishes, either successfully or not.
   */
  @VisibleForTesting
  void waitUntilPersistentCacheCreated() throws ExecutionException, InterruptedException {
    myCacheCreatedFuture.get();
  }

  @Override
  @NotNull
  public Set<ResourceType> getResourceTypes(@NotNull ResourceNamespace namespace) {
    return namespace == ANDROID_NAMESPACE ? Sets.immutableEnumSet(myResources.keySet()) : ImmutableSet.of();
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
    byte[] header = getCacheFileHeader();
    return loadFromPersistentCache(getCacheFile(), header);
  }

  private void createPersistentCache() {
    byte[] header = getCacheFileHeader();
    createPersistentCache(getCacheFile(), header);
  }

  private byte[] getCacheFileHeader() {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    try (Base128OutputStream stream = new Base128OutputStream(header)) {
      stream.write(CACHE_FILE_HEADER);
      stream.writeString(CACHE_FILE_FORMAT_VERSION);
      stream.writeString(myResourceDirectory.toString());
      stream.writeBoolean(myWithLocaleResources);
      stream.writeString(getAndroidPluginVersion());
    }
    catch (IOException e) {
      throw new Error("Internal error", e); // An IOException in the try block above indicates a bug.
    }
    return header.toByteArray();
  }

  @NotNull
  private Path getCacheFile() {
    return getCacheFile(myResourceDirectory, myWithLocaleResources);
  }

  @NotNull
  private static String getAndroidPluginVersion() {
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(ANDROID_PLUGIN_ID));
    if (plugin == null) {
      return "unknown";
    }
    return plugin.getVersion();
  }

  private class MyLoader extends Loader {
    @Override
    public boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs) {
      if (super.isIgnored(fileOrDirectory, attrs)) {
        return true;
      }

      String fileName = fileOrDirectory.getFileName().toString();
      if (attrs.isDirectory()) {
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
      }
      else if ((fileName.equals("public.xml") || fileName.equals("symbols.xml")) &&
               "values".equals(new PathString(fileOrDirectory).getParentFileName())) {
        return true; // Skip files that don't contain resources.
      }

      return false;
    }

    @Override
    protected void loadPublicResourceNames() {
      Path valuesFolder = myResourceDirectory.resolve(SdkConstants.FD_RES_VALUES);
      Path publicXmlFile = valuesFolder.resolve("public.xml");

      try (InputStream stream = new BufferedInputStream(Files.newInputStream(publicXmlFile))) {
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
                type = ResourceType.fromXmlValue(typeName);
                lastType = type;
                lastTypeName = typeName;
              }

              if (type != null) {
                Set<String> names = myPublicResources.computeIfAbsent(type, t -> new HashSet<>());
                names.add(name);
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
      } catch (NoSuchFileException e) {
        // There is no public.xml. This not considered an error.
      } catch (Exception e) {
        LOG.error("Can't read and parse public attribute list " + publicXmlFile.toString(), e);
      }
    }

    @Override
    @NotNull
    protected String getKeyForVisibilityLookup(@NotNull String resourceName) {
      // This class obtains names of public resources from public.xml where all resource names are preserved
      // in their original form. This is different from the superclass that obtains the names from public.txt
      // where the names are transformed by replacing dots, colons and dashes with underscores.
      return resourceName;
    }
  }
}
