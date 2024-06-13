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

import static com.android.SdkConstants.EXT_CSV;
import static com.android.SdkConstants.EXT_JSON;
import static com.intellij.openapi.application.ActionsKt.runReadAction;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SampleDataResourceValueImpl;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.sampledata.SampleDataCsvParser;
import com.android.ide.common.resources.sampledata.SampleDataHolder;
import com.android.ide.common.resources.sampledata.SampleDataJsonParser;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.sampledata.datasource.HardcodedContent;
import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class defines a sample data source. It also handles the caching and invalidation according
 * to the given functions passed in the creation.
 */
public class SampleDataResourceItem implements ResourceItem, ResolvableResourceItem {
  private static final Logger LOG = Logger.getInstance(SampleDataResourceItem.class);

  private static final Cache<String, SampleDataHolder> sSampleDataCache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(2, TimeUnit.MINUTES)
      .softValues()
      .weigher((String key, SampleDataHolder value) -> value.getFileSizeMb()) // Length returns unicode codepoints so not exactly in MB.
      .maximumWeight(50) // MB
      .build();

  @NotNull private final SingleNamespaceResourceRepository myOwner;
  @NotNull private final String myName;
  @NotNull private final Function<OutputStream, Exception> myDataSource;
  @NotNull private final Supplier<Long> myDataSourceModificationStamp;
  @Nullable private final SmartPsiElementPointer<PsiElement> mySourceElement;

  @Nullable private ResourceValue myResourceValue;
  @NotNull private final ContentType myContentType;

  /**
   * Creates a new {@link SampleDataResourceItem}.
   *
   * @param owner the resource repository owning this resource
   * @param name the name of the resource
   * @param dataSource {@link Function} that writes the content to be used for this item to the passed {@link OutputStream}. The function
   *     must return any exceptions that happened during the processing of the file.
   * @param dataSourceModificationStamp {@link Supplier} that returns a modification stamp. This stamp should change every time the content
   *     changes. If 0, the content won't be cached.
   * @param sourceElement optional {@link SmartPsiElementPointer} where the content was obtained from. This will be used to display
   *     references to the content.
   */
  private SampleDataResourceItem(@NotNull SingleNamespaceResourceRepository owner,
                                 @NotNull String name,
                                 @NotNull Function<OutputStream, Exception> dataSource,
                                 @NotNull Supplier<Long> dataSourceModificationStamp,
                                 @Nullable SmartPsiElementPointer<PsiElement> sourceElement,
                                 @NotNull ContentType contentType) {
    myOwner = owner;
    myName = name;
    myDataSource = dataSource;
    myDataSourceModificationStamp = dataSourceModificationStamp;
    // We use SourcelessResourceItem as parent because we don't really obtain a FolderConfiguration or Qualifiers from
    // the source element (since it's not within the resources directory).
    mySourceElement = sourceElement;
    myContentType = contentType;
  }

  /**
   * Invalidates contents of the sample data cache.
   */
  public static void invalidateCache() {
    sSampleDataCache.invalidateAll();
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given static content generator. Static content generators can be cached indefinitely
   * since the never change.
   */
  @NotNull
  static SampleDataResourceItem getFromStaticDataSource(@NotNull SingleNamespaceResourceRepository repository,
                                                        @NotNull String name,
                                                        @NotNull Function<OutputStream, Exception> source,
                                                        @NotNull ContentType contentType) {
    return new SampleDataResourceItem(repository, name, source, () -> 1L, null, contentType);
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given {@link SmartPsiElementPointer<PsiElement>}. The file is tracked to invalidate
   * the contents of the {@link SampleDataResourceItem} if the source element changes.
   */
  @NotNull
  private static SampleDataResourceItem getFromPlainFile(@NotNull SingleNamespaceResourceRepository repository,
                                                         @NotNull SmartPsiElementPointer<PsiElement> filePointer) {
    VirtualFile vFile = filePointer.getVirtualFile();
    String fileName = vFile.getName();
    PsiElement sourceElement = filePointer.getElement();
    boolean isImageType = (sourceElement instanceof PsiBinaryFile
                           && ((PsiBinaryFile)sourceElement).getFileType() == ImageFileTypeManager.getInstance().getImageFileType());

    return new SampleDataResourceItem(repository,
                                      fileName,
                                      output -> {
                                        if (sourceElement == null) {
                                          LOG.warn("File pointer was invalidated and the repository was not refreshed");
                                          return null;
                                        }

                                        try {
                                          if (isImageType) {
                                            output.write(vFile.getPath().getBytes(UTF_8));
                                          }
                                          else {
                                            output.write(sourceElement.getText().getBytes(UTF_8));
                                          }
                                        }
                                        catch (IOException e) {
                                          LOG.warn("Unable to load content from plain file " + fileName, e);
                                          return e;
                                        }
                                        return null;
                                      },
                                      () -> vFile.getModificationStamp() + 1, filePointer,
                                      isImageType ? ContentType.IMAGE : ContentType.UNKNOWN);
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given {@link SmartPsiElementPointer<PsiElement>}. The directory is tracked to
   * invalidate the contents of the {@link SampleDataResourceItem} if the directory contents change.
   */
  @NotNull
  private static SampleDataResourceItem getFromDirectory(@NotNull SingleNamespaceResourceRepository repository,
                                                         @NotNull SmartPsiElementPointer<PsiElement> directoryPointer) {
    VirtualFile directory = directoryPointer.getVirtualFile();
    // For directories, at this point, we always consider them images since it's the only type we handle for them
    return new SampleDataResourceItem(repository, directory.getName(), output -> {
      try (PrintStream printStream = new PrintStream(output, false, UTF_8.name())) {
        Arrays.stream(directory.getChildren())
              .filter(child -> !child.isDirectory())
              .sorted(Comparator.comparing(VirtualFile::getName))
              .forEach(file -> printStream.println(file.getPath()));
      }
      catch (UnsupportedEncodingException e) {
        LOG.error(e);
      }
      return null;
    }, () -> directory.getModificationStamp() + 1, directoryPointer, ContentType.IMAGE);
  }

  /**
   * Similar to {@link #getFromPlainFile} but it takes a JSON file and a path as inputs. The {@link SampleDataResourceItem}
   * will be the selection of elements from the sourceElement that are found with the given path.
   */
  @NotNull
  private static SampleDataResourceItem getFromJsonFile(@NotNull SingleNamespaceResourceRepository repository,
                                                        @NotNull SmartPsiElementPointer<PsiElement> jsonPointer,
                                                        @NotNull String contentPath) {
    VirtualFile vFile = jsonPointer.getVirtualFile();
    String fileName = vFile.getName();
    return new SampleDataResourceItem(repository, fileName + contentPath, output -> {
      if (contentPath.isEmpty()) {
        return null;
      }

      PsiElement source = jsonPointer.getElement();
      if (source == null) {
        LOG.warn("JSON file pointer was invalidated and the repository was not refreshed");
        return null;
      }

      try {
        StringReader input = new StringReader(source.getText());
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

  @NotNull
  private static String getTextFromPsiElementPointer(SmartPsiElementPointer<PsiElement> pointer) {
    PsiElement rootJsonElement = pointer.getElement();
    return rootJsonElement != null ? rootJsonElement.getText() : "";
  }

  /**
   * Returns a {@link SampleDataResourceItem}s from the given {@link PsiFileSystemItem}. The method will detect the type
   * of file or directory and return a number of items.
   */
  @NotNull
  public static List<SampleDataResourceItem> getFromPsiFileSystemItem(@NotNull SingleNamespaceResourceRepository repository,
                                                                      @NotNull PsiFileSystemItem sampleDataSource) throws IOException {
    String extension = sampleDataSource.getVirtualFile().getExtension();
    if (extension == null) {
      extension = "";
    }

    SmartPsiElementPointer<PsiElement> psiPointer = runReadAction(
      () -> SmartPointerManager.getInstance(sampleDataSource.getProject()).createSmartPsiElementPointer(sampleDataSource)
    );

    switch (extension) {
      case EXT_JSON: {
        SampleDataJsonParser parser;
        String jsonText = getTextFromPsiElementPointer(psiPointer);
        try (StringReader reader = new StringReader(jsonText)) {
          parser = SampleDataJsonParser.parse(reader);
        }
        if (parser == null) {
          // Failed to parse the JSON file
          return Collections.emptyList();
        }

        Set<String> possiblePaths = parser.getPossiblePaths();
        ImmutableList.Builder<SampleDataResourceItem> items = ImmutableList.builder();
        for (String path : possiblePaths) {
          items.add(getFromJsonFile(repository, psiPointer, path));
        }
        return items.build();
      }

      case EXT_CSV: {
        SampleDataCsvParser parser;
        String csvText = getTextFromPsiElementPointer(psiPointer);
        try (StringReader reader = new StringReader(csvText)) {
          parser = SampleDataCsvParser.parse(reader);
        }

        Set<String> possiblePaths = parser.getPossiblePaths();
        ImmutableList.Builder<SampleDataResourceItem> items = ImmutableList.builder();
        for (String path : possiblePaths) {
          items.add(new SampleDataResourceItem(repository, sampleDataSource.getName() + path,
                                               new HardcodedContent(Joiner.on('\n').join(parser.getPath(path))),
                                               () -> psiPointer.getVirtualFile().getModificationStamp() + 1, psiPointer,
                                               ContentType.UNKNOWN));
        }
        return items.build();
      }

      default:
        return ImmutableList.of(sampleDataSource instanceof PsiDirectory ?
                                getFromDirectory(repository, psiPointer) : getFromPlainFile(repository, psiPointer));
    }
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
  public String getValueText() {
    byte[] content = getContent(null);
    return content != null ? new String(content, UTF_8) : null;
  }

  @Override
  @NotNull
  public SingleNamespaceResourceRepository getRepository() {
    return myOwner;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public ResourceType getType() {
    return ResourceType.SAMPLE_DATA;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myOwner.getNamespace();
  }

  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    return DEFAULT_CONFIGURATION;
  }

  @Override
  @NotNull
  public ResourceValue getResourceValue() {
    byte[] content = getContent(() -> myResourceValue = null);
    if (myResourceValue == null) {
      myResourceValue = new SampleDataResourceValueImpl(getReferenceToSelf(), content);
    }
    return myResourceValue;
  }

  @Override
  @Nullable
  public PathString getSource() {
    return null;
  }

  @Override
  public boolean isFileBased() {
    return false;
  }

  @Override
  @NotNull
  public ResolveResult createResolveResult() {
    return new ResolveResult() {
      @Override
      @Nullable
      public PsiElement getElement() {
        return mySourceElement != null ? mySourceElement.getElement() : null;
      }

      @Override
      public boolean isValidResult() {
        return true;
      }
    };
  }

  @NotNull
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
