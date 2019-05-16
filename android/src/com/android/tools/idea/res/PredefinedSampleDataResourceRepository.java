/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.res.SampleDataResourceItem.ContentType.IMAGE;
import static com.android.tools.idea.res.SampleDataResourceItem.ContentType.TEXT;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.sampledata.datasource.CombinerDataSource;
import com.android.tools.idea.sampledata.datasource.DateTimeGenerator;
import com.android.tools.idea.sampledata.datasource.LoremIpsumGenerator;
import com.android.tools.idea.sampledata.datasource.NumberGenerator;
import com.android.tools.idea.sampledata.datasource.ResourceContent;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A repository of predefined sample data. All predefined resource items belong to the {@link ResourceNamespace#TOOLS} namespace.
 */
public final class PredefinedSampleDataResourceRepository extends AbstractResourceRepository implements SingleNamespaceResourceRepository {
  public static final ResourceNamespace NAMESPACE = ResourceNamespace.TOOLS;

  /**
   * List of predefined data sources that are always available within Studio.
   */
  private static final SampleDataResourceItem[] PREDEFINED_SOURCES = {
    SampleDataResourceItem.getFromStaticDataSource("full_names",
                                                   new CombinerDataSource(
                                                       getResourceAsStream("sampleData/names.txt"),
                                                       getResourceAsStream("sampleData/surnames.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("first_names",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/names.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("last_names",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/surnames.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("cities",
                                                   ResourceContent.fromInputStream(getResourceAsStream("sampleData/cities.txt")),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("us_zipcodes",
                                                   new NumberGenerator("%05d", 20000, 99999),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("us_phones",
                                                   new NumberGenerator("(800) 555-%04d", 0, 9999),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("lorem", new LoremIpsumGenerator(false),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("lorem/random", new LoremIpsumGenerator(true),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("avatars",
                                                   ResourceContent.fromDirectory("avatars"),
                                                   IMAGE),
    SampleDataResourceItem.getFromStaticDataSource("backgrounds/scenic",
                                                   ResourceContent.fromDirectory("backgrounds/scenic"),
                                                   IMAGE),

    // TODO: Delegate path parsing to the data source to avoid all these declarations.
    SampleDataResourceItem.getFromStaticDataSource("date/day_of_week",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("E"), ChronoUnit.DAYS),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("date/ddmmyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("dd-MM-yy"), ChronoUnit.DAYS),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("date/mmddyy",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("MM-dd-yy"), ChronoUnit.DAYS),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmm",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm"), ChronoUnit.MINUTES),
                                                   TEXT),
    SampleDataResourceItem.getFromStaticDataSource("date/hhmmss",
                                                   new DateTimeGenerator(DateTimeFormatter.ofPattern("hh:mm:ss"), ChronoUnit.SECONDS),
                                                   TEXT)
  };

  @NotNull private static final PredefinedSampleDataResourceRepository INSTANCE = new PredefinedSampleDataResourceRepository();

  @NotNull private final ImmutableListMultimap<String, ResourceItem> myResources;

  @NotNull
  public static PredefinedSampleDataResourceRepository getInstance() {
    return INSTANCE;
  }

  private PredefinedSampleDataResourceRepository() {
    ImmutableListMultimap.Builder<String, ResourceItem> builder = ImmutableListMultimap.builder();
    for (SampleDataResourceItem resource : PREDEFINED_SOURCES) {
      builder.put(resource.getName(), resource);
    }
    myResources = builder.build();
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return NAMESPACE;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return NAMESPACE.getPackageName();
  }

  @Override
  @NotNull
  protected ListMultimap<String, ResourceItem> getResourcesInternal(@NotNull ResourceNamespace namespace,
                                                                    @NotNull ResourceType resourceType) {
    return namespace.equals(NAMESPACE) && resourceType == ResourceType.SAMPLE_DATA ? myResources : ImmutableListMultimap.of();
  }

  @Override
  public void accept(@NotNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(NAMESPACE) && visitor.shouldVisitResourceType(ResourceType.SAMPLE_DATA)) {
      for (ResourceItem item : myResources.values()) {
        if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
          return;
        }
      }
    }
  }

  @Override
  @NotNull
  public Collection<ResourceItem> getPublicResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    return myResources.values();
  }

  private static InputStream getResourceAsStream(@NotNull String name) {
    return PredefinedSampleDataResourceRepository.class.getClassLoader().getResourceAsStream(name);
  }
}
