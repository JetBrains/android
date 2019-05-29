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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

/**
 * Repository of resources of the Android framework. Most client code should use
 * the ResourceRepositoryManager.getFrameworkResources method to obtain framework resources.
 *
 * <p>The repository can be loaded either from a res directory containing XML files, or from
 * framework_res.jar file, or from a binary cache file located under the directory returned by
 * the {@link PathManager#getSystemPath()} method. This binary cache file can be created as
 * a side effect of loading the repository from a res directory.
 *
 * <p>Loading from framework_res.jar or a binary cache file is 3-4 times faster than loading
 * from res directory.
 *
 * @see FrameworkResJarCreator
 */
public final class FrameworkResourceRepository extends AarSourceResourceRepository {
  private static final ResourceNamespace ANDROID_NAMESPACE = ResourceNamespace.ANDROID;
  static final String ENTRY_NAME_WITH_LOCALES = "resources.bin";
  static final String ENTRY_NAME_WITHOUT_LOCALES = "resources_light.bin";

  private static final Logger LOG = Logger.getInstance(FrameworkResourceRepository.class);

  private final boolean myWithLocaleResources;

  private FrameworkResourceRepository(@NotNull MyLoader loader) {
    super(loader);
    myWithLocaleResources = loader.myWithLocaleResources;
  }

  /**
   * Creates an Android framework resource repository without using a persistent cache.
   *
   * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
   * @return the created resource repository
   */
  @NotNull
  public static FrameworkResourceRepository create(@NotNull Path resourceDirectoryOrFile, boolean withLocaleResources) {
    return create(resourceDirectoryOrFile, withLocaleResources, null);
  }

  /**
   * Creates an Android framework resource repository.
   *
   * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
   * @param withLocaleResources whether to include locale-specific resources or not
   * @param cachingData data used to validate and create a persistent cache file
   * @return the created resource repository
   */
  @NotNull
  public static FrameworkResourceRepository create(@NotNull Path resourceDirectoryOrFile, boolean withLocaleResources,
                                                   @Nullable CachingData cachingData) {
    MyLoader loader = new MyLoader(resourceDirectoryOrFile, withLocaleResources);
    FrameworkResourceRepository repository = new FrameworkResourceRepository(loader);

    // If not loading from a jar file, try to load from a cache file first. A separate cache file is not used
    // when loading from framework_res.jar since it already contains data in the cache format. Loading from
    // framework_res.jar or a cache file is significantly faster than reading individual resource files.
    if (!loader.myLoadFromZipArchive && cachingData != null && repository.loadFromPersistentCache(cachingData)) {
      return repository;
    }

    loader.loadRepositoryContents(repository);

    if (!loader.myLoadFromZipArchive && cachingData != null) {
      Executor executor = cachingData.getCacheCreationExecutor();
      if (executor != null) {
        executor.execute(() -> repository.createPersistentCache(cachingData));
      }
    }
    return repository;
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

  @Override
  protected void writeCacheHeaderContent(@NotNull CachingData cachingData, @NotNull Base128OutputStream stream) throws IOException {
    super.writeCacheHeaderContent(cachingData, stream);
    stream.writeBoolean(myWithLocaleResources);
  }

  private static class MyLoader extends Loader {
    private final boolean myWithLocaleResources;

    MyLoader(@NotNull Path resourceDirectoryOrFile, boolean withLocaleResources) {
      super(resourceDirectoryOrFile, null, ANDROID_NAMESPACE, null);
      myWithLocaleResources = withLocaleResources;
    }

    @Override
    protected void loadFromZip(@NotNull AarSourceResourceRepository repository) {
      try (ZipFile zipFile = new ZipFile(myResourceDirectoryOrFile.toFile())) {
        String entryName = myWithLocaleResources ? ENTRY_NAME_WITH_LOCALES : ENTRY_NAME_WITHOUT_LOCALES;
        ZipEntry zipEntry = zipFile.getEntry(entryName);
        if (zipEntry == null) {
          throw new IOException("\"" + entryName + "\" not found in " + myResourceDirectoryOrFile.toString());
        }

        try (Base128InputStream stream = new Base128InputStream(zipFile.getInputStream(zipEntry))) {
          repository.loadFromStream(stream);
        }
      }
      catch (Exception e) {
        LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
      }
    }

    @Override
    protected boolean useRTxt() {
      return false; // Framework resources don't contain R.txt file.
    }

    @Override
    public boolean isIgnored(@NotNull Path fileOrDirectory, @NotNull BasicFileAttributes attrs) {
      if (super.isIgnored(fileOrDirectory, attrs)) {
        return true;
      }

      String fileName = fileOrDirectory.getFileName().toString();
      if (attrs.isDirectory()) {
        if (fileName.startsWith("values-mcc") ||
            fileName.startsWith("raw") && (fileName.length() == "raw".length() || fileName.charAt("raw".length()) == '-')) {
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
      Path valuesFolder = myResourceDirectoryOrFile.resolve(SdkConstants.FD_RES_VALUES);
      Path publicXmlFile = valuesFolder.resolve("public.xml");

      try (InputStream stream = new BufferedInputStream(Files.newInputStream(publicXmlFile))) {
        KXmlParser parser = new KXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(stream, UTF_8.name());

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
