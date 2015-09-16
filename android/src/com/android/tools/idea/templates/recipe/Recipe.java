/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe;

import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.parse.StringFileAdapter;
import com.google.common.collect.Lists;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.Reader;
import java.util.List;

import static com.android.SdkConstants.DOT_FTL;

/**
 * A recipe is a list of instructions which, when executed, modify a project.
 */
@XmlRootElement(name = "recipe")
public final class Recipe {
  // @formatter:off
  @XmlElements({
    @XmlElement(name = "copy", type = CopyInstruction.class),
    @XmlElement(name = "instantiate", type = InstantiateInstruction.class),
    @XmlElement(name = "merge", type = MergeInstruction.class),
    @XmlElement(name = "mkdir", type = MkDirInstruction.class),
    @XmlElement(name = "dependency", type = DependencyInstruction.class),
    @XmlElement(name = "open", type = OpenInstruction.class),
    @XmlElement(name = "execute", type = ExecuteInstruction.class)
  })
  private List<RecipeInstruction> instructions = Lists.newArrayList();
  // @formatter:on

  @NotNull
  private final List<String> myDependencies = Lists.newArrayList();
  @NotNull
  private final List<File> mySourceFiles = Lists.newArrayList();
  @NotNull
  private final List<File> myTargetFiles = Lists.newArrayList();
  @NotNull
  private final List<File> myFilesToOpen = Lists.newArrayList();

  /**
   * True if this recipe is a parent (executes child recipes in execution instructions).
   */
  private boolean myIsParent;

  @NotNull
  public List<String> getDependencies() {
    return myDependencies;
  }

  @NotNull
  public List<File> getSourceFiles() {
    return mySourceFiles;
  }

  @NotNull
  public List<File> getTargetFiles() {
    return myTargetFiles;
  }

  @NotNull
  public List<File> getFilesToOpen() {
    return myFilesToOpen;
  }

  /**
   * Handles parsing a recipe.xml file. A recipe file specifies a bunch of file-related actions
   * to take after a template is processed, such as copying files over, merging them, or opening them
   * in the main editor. Once parsed, you should remember to {@link #execute(RecipeContext)} it.
   */
  public static Recipe parse(@NotNull Reader xmlReader) throws JAXBException {
    JAXBContext jc = JAXBContext.newInstance(Recipe.class);
    Unmarshaller u = jc.createUnmarshaller();
    return (Recipe)u.unmarshal(xmlReader);
  }

  @NotNull
  private static File cloneWithourFreemarkerSuffix(@NotNull File file) {
    File clone = new File(file.getPath());
    return TemplateUtils.stripSuffix(clone, DOT_FTL);
  }

  /**
   * Execute this recipe's instructions.
   */
  public void execute(@NotNull RecipeContext recipeContext) {
    for (RecipeInstruction instruction : instructions) {
      instruction.execute(this, recipeContext);
    }
  }

  @SuppressWarnings("unused") // Called by JAXB via reflection
  private void afterUnmarshal(Unmarshaller u, Object parent) {
    instructions.add(new FinalInstruction());

    for (RecipeInstruction instruction : instructions) {
      instruction.addDependenciesInto(myDependencies);
      instruction.addSourceFilesInto(mySourceFiles);
      instruction.addTargetFilesInto(myTargetFiles);
      instruction.addFilesToOpenInto(myFilesToOpen);
    }
  }

  /**
   * A single instruction in a recipe. Each implementation corresponds to a tag in recipe.xml
   */
  private static abstract class RecipeInstruction {
    abstract void execute(@NotNull Recipe recipe, @NotNull RecipeContext context);

    public void addDependenciesInto(@NotNull List<String> dependencies) {}

    public void addSourceFilesInto(@NotNull List<File> files) {}

    public void addTargetFilesInto(@NotNull List<File> files) {}

    public void addFilesToOpenInto(@NotNull List<File> files) {}
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class CopyInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      assert to != null; // Will be non-null after afterUnmarshal is called
      context.copy(from, to);
    }

    @Override
    public void addSourceFilesInto(@NotNull List<File> files) {
      files.add(from);
    }

    @Override
    public void addTargetFilesInto(@NotNull List<File> files) {
      files.add(to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithourFreemarkerSuffix(from);
      }
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class InstantiateInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      assert to != null; // Will be non-null after afterUnmarshal is called
      context.instantiate(from, to);
    }

    @Override
    public void addSourceFilesInto(@NotNull List<File> files) {
      files.add(from);
    }

    @Override
    public void addTargetFilesInto(@NotNull List<File> files) {
      files.add(to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithourFreemarkerSuffix(from);
      }
    }

  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class MergeInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      assert to != null; // Will be non-null after afterUnmarshal is called
      context.merge(from, to);
    }

    @Override
    public void addSourceFilesInto(@NotNull List<File> files) {
      files.add(from);
    }

    @Override
    public void addTargetFilesInto(@NotNull List<File> files) {
      files.add(to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithourFreemarkerSuffix(from);
      }
    }

  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class MkDirInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File at;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      context.mkDir(at);
    }

  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class OpenInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File file;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
    }

    @Override
    public void addFilesToOpenInto(@NotNull List<File> files) {
      files.add(file);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class DependencyInstruction extends RecipeInstruction {
    @XmlAttribute(required = true)
    @NotNull
    private String mavenUrl;

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      context.addDependency(mavenUrl);
    }

    @Override
    public void addDependenciesInto(@NotNull List<String> dependencies) {
      dependencies.add(mavenUrl);
    }
  }

  /**
   * Execute another recipe file from within the current recipe file
   */
  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class ExecuteInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File file;

    @Override
    void execute(@NotNull final Recipe parent, @NotNull RecipeContext context) {
      context.parseRecipe(file, new Consumer<Recipe>() {
        @Override
        public void consume(Recipe child) {
          parent.myDependencies.addAll(child.myDependencies);
          parent.mySourceFiles.addAll(child.mySourceFiles);
          parent.myTargetFiles.addAll(child.myTargetFiles);
          parent.myFilesToOpen.addAll(child.myFilesToOpen);

          parent.myIsParent = true;
        }
      });
    }
  }

  /**
   * Post-process instruction that's always added to the end of a recipe's instruction list.
   */
  private static final class FinalInstruction extends RecipeInstruction {

    @Override
    void execute(@NotNull Recipe recipe, @NotNull RecipeContext context) {
      if (recipe.myIsParent) {
        context.updateAndSyncGradle();
      }
    }
  }
}
