/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Objects;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * UI information about individual template.
 *
 * @deprecated Replaced by {@link TemplateHandle}
 */
public class TemplateEntry {
  private final File myTemplate;
  private final TemplateMetadata myMetadata;

  public TemplateEntry(File template, TemplateMetadata metadata) {
    myTemplate = template;
    myMetadata = metadata;
  }

  @Nullable
  public Image getImage() {
    String thumb = myMetadata.getThumbnailPath();
    if (thumb != null && !thumb.isEmpty()) {
      try {
        File file = new File(myTemplate, thumb.replace('/', File.separatorChar));
        if (file.isFile()) {
          return ImageIO.read(file);
        }
        else {
          return null;
        }
      }
      catch (IOException e) {
        Logger.getInstance(ActivityGalleryStep.class).warn(e);
      }
    }
    return null;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myTemplate, getTitle());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    else if (obj == this) {
      return true;
    }
    else {
      TemplateEntry another = (TemplateEntry)obj;
      return Objects.equal(myTemplate, another.myTemplate) &&
             Objects.equal(getTitle(), another.getTitle());
    }
  }

  public Template getTemplate() {
    return Template.createFromPath(myTemplate);
  }

  @NotNull
  public TemplateMetadata getMetadata() {
    return myMetadata;
  }

  @Nullable
  public String getTitle() {
    return myMetadata.getTitle();
  }

  @Nullable
  public String getDescription() {
    return getMetadata().getDescription();
  }

  public int getMinBuildApi() {
    return getMetadata().getMinBuildApi();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("title", getTitle()).
      add("path", myTemplate.getAbsolutePath()).toString();
  }

  public int getMinSdk() {
    return getMetadata().getMinSdk();
  }

  public Collection<Parameter> getParameters() {
    return getMetadata().getParameters();
  }

  public File getTemplateDir() {
    return myTemplate;
  }
}
