/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.resources.SourcelessResourceItem;
import com.android.ide.common.resources.sampledata.SampleDataCsvParser;
import com.android.ide.common.resources.sampledata.SampleDataHolder;
import com.android.ide.common.resources.sampledata.SampleDataJsonParser;
import com.android.resources.ResourceType;
import com.android.tools.idea.sampledata.datasource.HardcodedContent;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.w3c.dom.Node;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.android.SdkConstants.EXT_CSV;
import static com.android.SdkConstants.EXT_JSON;

/**
 * This class defines a sample data source. It also handles the caching and invalidation according
 * to the given functions passed in the creation.
 */
public class SampleDataResourceItem extends SourcelessResourceItem {
  private static final Logger LOG = Logger.getInstance(SampleDataResourceItem.class);

  private static final Cache<String, SampleDataHolder> sSampleDataCache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(2, TimeUnit.MINUTES)
      .softValues()
      .weigher((String key, SampleDataHolder value) -> value.getFileSizeMb()) // length returns unicode codepoints so not exactly in MB
      .maximumWeight(50) // MB
      .build();

  private final Function<OutputStream, Exception> myDataSource;
  private final SmartPsiElementPointer<PsiElement> mySourceElement;
  private final Supplier<Long> myDataSourceModificationStamp;
  private final ContentType myContentType;

  /**
   * Creates a new {@link SampleDataResourceItem}
   *
   * @param name                        name of the resource
   * @param namespace                   optional resource namespace. Pre-defined data sources use the {@link ResourceNamespace#TOOLS} namespace.
   * @param dataSource                  {@link Function} that writes the content to be used for this item to the passed {@link OutputStream}. The function
   *                                    must return any exceptions that happened during the processing of the file.
   * @param dataSourceModificationStamp {@link Supplier} that returns a modification stamp. This stamp should change every time the
   *                                    content changes. If 0, the content won't be cached.
   * @param sourceElement               optional {@link SmartPsiElementPointer} where the content was obtained from. This will be used to display
   *                                    references to the content.
   */
  private SampleDataResourceItem(@NonNull String name,
                                 @NonNull ResourceNamespace namespace,
                                 @NonNull Function<OutputStream, Exception> dataSource,
                                 @NonNull Supplier<Long> dataSourceModificationStamp,
                                 @Nullable SmartPsiElementPointer<PsiElement> sourceElement,
                                 @NonNull ContentType contentType) {
    super(name, namespace, ResourceType.SAMPLE_DATA, null, null);

    myDataSource = dataSource;
    myDataSourceModificationStamp = dataSourceModificationStamp;
    // We use SourcelessResourceItem as parent because we don't really obtain a FolderConfiguration or Qualifiers from
    // the source element (since it's not within the resources directory).
    mySourceElement = sourceElement;
    myContentType = contentType;
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given static content generator. Static content generators can be cached indefinitely
   * since the never change.
   */
  @NonNull
  static SampleDataResourceItem getFromStaticDataSource(@NonNull String name,
                                                        @NonNull Function<OutputStream, Exception> source,
                                                        @NonNull ContentType contentType) {
    return new SampleDataResourceItem(name, SampleDataResourceRepository.PREDEFINED_SAMPLES_NS, source, () -> 1L, null, contentType);
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given {@link SmartPsiElementPointer<PsiElement>}. The file is tracked to invalidate
   * the contents of the {@link SampleDataResourceItem} if the sourceElement changes.
   */
  @NonNull
  private static SampleDataResourceItem getFromPlainFile(@NonNull SmartPsiElementPointer<PsiElement> filePointer) {
    VirtualFile vFile = filePointer.getVirtualFile();
    String fileName = vFile.getName();

    return new SampleDataResourceItem(fileName, ResourceNamespace.TODO, output -> {
      PsiElement sourceElement = filePointer.getElement();
      if (sourceElement == null) {
        LOG.warn("File pointer was invalidated and the repository was not refreshed");
        return null;
      }

      try {
        output.write(sourceElement.getText().getBytes(Charsets.UTF_8));
      }
      catch (IOException e) {
        LOG.warn("Unable to load content from plain file " + fileName, e);
        return e;
      }
      return null;
    }, () -> vFile.getModificationStamp() + 1, filePointer, ContentType.UNKNOWN);
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given {@link SmartPsiElementPointer<PsiElement>}. The directory is tracked to
   * invalidate the contents of the {@link SampleDataResourceItem} if the directory contents change.
   */
  @NonNull
  private static SampleDataResourceItem getFromDirectory(@NonNull SmartPsiElementPointer<PsiElement> directoryPointer) {
    VirtualFile directory = directoryPointer.getVirtualFile();
    return new SampleDataResourceItem(directory.getName(), ResourceNamespace.TODO, output -> {
      PrintStream printStream = new PrintStream(output);
      Arrays.stream(directory.getChildren())
        .filter(child -> !child.isDirectory())
        .sorted(Comparator.comparing(VirtualFile::getName))
        .forEach(file -> printStream.println(file.getPath()));
      return null;
    }, () -> directory.getModificationStamp() + 1, directoryPointer, ContentType.UNKNOWN);
  }

  /**
   * Similar to {@link SampleDataResourceItem#getFromPlainFile(SmartPsiElementPointer)} but it takes a JSON file and a path as inputs.
   * The {@link SampleDataResourceItem} will be the selection of elements from the sourceElement that are found with the
   * given path.
   */
  @NonNull
  private static SampleDataResourceItem getFromJsonFile(@NonNull SmartPsiElementPointer<PsiElement> jsonPointer,
                                                        @NonNull String contentPath) {
    VirtualFile vFile = jsonPointer.getVirtualFile();
    String fileName = vFile.getName();
    return new SampleDataResourceItem(fileName + contentPath, ResourceNamespace.TODO, output -> {
      if (contentPath.isEmpty()) {
        return null;
      }

      PsiElement source = jsonPointer.getElement();
      if (source == null) {
        LOG.warn("JSON file pointer was invalidated and the repository was not refreshed");
        return null;
      }

      try {
        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(source.getText().getBytes(Charsets.UTF_8)));
        SampleDataJsonParser parser = SampleDataJsonParser.parse(input);
        if (parser != null) {
          output.write(parser.getContentFromPath(contentPath));
        }
      }
      catch (IOException e) {
        LOG.warn("Unable to load content from json file " + fileName, e);
        return e;
      }
      return null;
    }, () -> vFile.getModificationStamp() + 1, jsonPointer, ContentType.UNKNOWN);
  }

  /**
   * Returns a {@link SampleDataResourceItem}s from the given {@link PsiFileSystemItem}. The method will detect the type
   * of file or directory and return a number of items.
   */
  @NonNull
  public static List<SampleDataResourceItem> getFromPsiFileSystemItem(@NonNull PsiFileSystemItem sampleDataSource) throws IOException {
    String extension = sampleDataSource.getVirtualFile().getExtension();
    if (extension == null) {
      extension = "";
    }

    SmartPsiElementPointer<PsiElement> psiPointer =
      SmartPointerManager.getInstance(sampleDataSource.getProject()).createSmartPsiElementPointer(sampleDataSource);

    switch (extension) {
      case EXT_JSON: {
        SampleDataJsonParser parser;
        try (FileReader reader = new FileReader(VfsUtilCore.virtualToIoFile(psiPointer.getVirtualFile()))) {
          parser = SampleDataJsonParser.parse(reader);
        }
        if (parser == null) {
          // Failed to parse the JSON file
          return Collections.emptyList();
        }

        Set<String> possiblePaths = parser.getPossiblePaths();
        ImmutableList.Builder<SampleDataResourceItem> items = ImmutableList.builder();
        for (String path : possiblePaths) {
          items.add(getFromJsonFile(psiPointer, path));
        }
        return items.build();
      }

      case EXT_CSV: {
        SampleDataCsvParser parser;
        VirtualFile vFile = sampleDataSource.getVirtualFile();
        try (FileReader reader = new FileReader(VfsUtilCore.virtualToIoFile(vFile))) {
          parser = SampleDataCsvParser.parse(reader);
        }
        Set<String> possiblePaths = parser.getPossiblePaths();
        ImmutableList.Builder<SampleDataResourceItem> items = ImmutableList.builder();
        for (String path : possiblePaths) {
          items.add(new SampleDataResourceItem(sampleDataSource.getName() + path, ResourceNamespace.TODO,
                                               new HardcodedContent(Joiner.on('\n').join(parser.getPossiblePaths())),
                                               () -> vFile.getModificationStamp() + 1, psiPointer, ContentType.UNKNOWN));
        }
        return items.build();
      }

      default:
        return ImmutableList.of(sampleDataSource instanceof PsiDirectory ? getFromDirectory(psiPointer) : getFromPlainFile(psiPointer));
    }
  }

  @Nullable
  @Override
  public Node getValue() {
    throw new UnsupportedOperationException("SampleDataResourceItem does not support getValue");
  }

  /**
   * Retrieves the content from the Sample Data cache. If the data was out of date and needed to be refreshed, the passed callback will
   * be called.
   */
  @Nullable
  private byte[] getContent(@Nullable Runnable onCachedOutOfDate) {
    SampleDataHolder value = sSampleDataCache.getIfPresent(getName());
    if (value == null
        || value.getLastModification() == 0
        || value.getLastModification() != myDataSourceModificationStamp.get()) {
      long lastModificationStamp = myDataSourceModificationStamp.get();
      try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Exception e = myDataSource.apply(output);

        if (e == null) {
          byte[] content = output.toByteArray();
          value = new SampleDataHolder(getName(), lastModificationStamp, content.length / 1_000_000, content);
          sSampleDataCache.put(getName(), value);
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }

      if (onCachedOutOfDate != null) {
        onCachedOutOfDate.run();
      }
    }

    return value != null ? value.getContents() : null;
  }

  @Nullable
  @Override
  public String getValueText() {
    byte[] content = getContent(null);
    return content != null ? new String(content, Charsets.UTF_8) : null;
  }

  @NonNull
  @Override
  public String getQualifiers() {
    return "";
  }

  @Nullable
  @Override
  public ResourceValue getResourceValue() {
    byte[] content = getContent(this::wasTouched);
    if (mResourceValue == null) {
      mResourceValue = new SampleDataResourceValue(getReferenceToSelf(), content);
    }
    return mResourceValue;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return mySourceElement != null ? mySourceElement.getElement() : null;
  }

  @NonNull
  public ContentType getContentType() {
    return myContentType;
  }

  /**
   * Defines the content of the sample data included in this item when know.
   */
  // TODO: Infer content type for non-predefined data sources
  public enum ContentType {
    UNKNOWN,
    /**
     * This item contains data suitable to be displayed as text (i.e. in a TextView)
     */
    TEXT,
    /**
     * This item contains data suitable to be displayed as an image (i.e. in a ImageView)
     */
    IMAGE
  }
}
