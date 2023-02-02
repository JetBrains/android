/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.*;

import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static com.android.tools.idea.LogAnonymizerUtil.isPublicClass;
import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * The {@linkplain ResourceClassGenerator} can generate R classes on the fly for a given resource repository.
 * <p>
 * This is used to supply R classes on demand for layoutlib in order to render custom views in AAR libraries,
 * since AAR libraries ship with the view classes but with the R classes stripped out (this is done deliberately
 * such that the actual resource id's can be computed at build time by the app using the AAR resources; otherwise
 * there could be id collisions.
 * <p>
 * However, note that the custom view code itself does not know what the actual application R class package will
 * be - and we don't rewrite bytecode at build time to tell it. Instead, the build system will generate multiple
 * R classes, one for each AAR (in addition to the real R class), and it will pick unique id's for all the
 * resources across the whole app, and then writes these unique id's into the R class for each AAR as well.
 * <p>
 * It is <b>that</b> R class we are generating on the fly here. We want to be able to render custom views even
 * if the full application has not been compiled yet, so if normal class loading fails to identify a R class,
 * this generator will be called. It uses the normal resource repository (already used during rendering to
 * look up resources such as string and style values), and based on the names there generates bytecode on the
 * fly which can then be loaded into the VM and handled by the class loader.
 * <p>
 * In non-namespaced projects, the R class for an aar should contain the resource references to resources from
 * the aar and all its dependencies. It is not straight-forward to get the list of dependencies after the creation
 * of the resource repositories for each aar. So, we use the app's resource repository and generate the R file
 * from it. This will break custom libraries that use reflection on the R class, but meh.
 * <p>
 * In namespaced projects the R class contains only resources from the aar itself and the repository used by the
 * {@link ResourceClassGenerator} should be the one created from the AAR.
 */
public class ResourceClassGenerator {
  private static final Logger LOG = Logger.getInstance(ResourceClassGenerator.class);

  public interface NumericIdProvider {
    /**
     * Counter that tracks when the provider has been reset. This counter will be increased in every reset.
     * If the ids returned by {@link #getOrGenerateId(ResourceReference)} are being cached, they must be invalidated when
     * the generation changes.
     */
    long getGeneration();
    int getOrGenerateId(@NotNull ResourceReference resourceReference);
  }

  private long myIdGeneratorGeneration = -1L;
  private Map<ResourceType, TObjectIntHashMap<String>> myCache;
  /** For int[] in styleables. The ints in styleables are stored in {@link #myCache}. */
  private Map<String, TIntArrayList> myStyleableCache;
  @NotNull private final ResourceRepository myResources;
  @NotNull private final NumericIdProvider myIdProvider;
  @NotNull private final ResourceNamespace myNamespace;

  private ResourceClassGenerator(@NotNull NumericIdProvider idProvider,
                                 @NotNull ResourceRepository resources,
                                 @NotNull ResourceNamespace namespace) {
    myIdProvider = idProvider;
    myResources = resources;
    myNamespace = namespace;
  }

  /**
   * Creates a new {@linkplain ResourceClassGenerator}.
   */
  @NotNull
  public static ResourceClassGenerator create(@NotNull NumericIdProvider manager,
                                              @NotNull ResourceRepository resources,
                                              @NotNull ResourceNamespace namespace) {
    return new ResourceClassGenerator(manager, resources, namespace);
  }

  /**
   * @param fqcn Fully qualified class name (as accepted by ClassLoader, or as returned by Class.getName())
   */
  @Nullable
  public byte[] generate(String fqcn) {
    String className = fqcn.replace('.', '/');

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("generate(%s)", anonymizeClassName(className)));
    }
    ClassWriter cw = new ClassWriter(0);  // Don't compute MAXS and FRAMES.
    cw.visit(V1_6, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, Type.getInternalName(Object.class), null);

    int index = className.lastIndexOf('$');
    if (index != -1) {
      String typeName = className.substring(index + 1);
      ResourceType type = ResourceType.fromClassName(typeName);
      if (type == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  type '%s' doesn't exist", typeName));
        }
        return null;
      }

      cw.visitInnerClass(className, className.substring(0, index), typeName, ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
      long currentIdGeneration = myIdProvider.getGeneration();
      if (myIdGeneratorGeneration != currentIdGeneration || myCache == null) {
        myCache = Maps.newHashMap();
        myStyleableCache = null;
        myIdGeneratorGeneration = currentIdGeneration;
      }
      if (type == ResourceType.STYLEABLE) {
        if (myStyleableCache == null) {
          myCache.put(ResourceType.STYLEABLE, new TObjectIntHashMap<>());
          myStyleableCache = Maps.newHashMap();
          generateStyleable(cw, className);
        }
        else {
          TObjectIntHashMap<String> indexFieldsCache = myCache.get(ResourceType.STYLEABLE);
          assert indexFieldsCache != null;
          generateFields(cw, indexFieldsCache);
          generateIntArraysFromCache(cw, className);
        }
      } else {
        TObjectIntHashMap<String> typeCache = myCache.get(type);
        if (typeCache == null) {
          typeCache = new TObjectIntHashMap<>();
          myCache.put(type, typeCache);
          generateValuesForType(cw, type, typeCache);
        }
        else {
          generateFields(cw, typeCache);
        }
      }
    } else {
      // Default R class.
      for (ResourceType t : myResources.getResourceTypes(myNamespace)) {
        if (t.getHasInnerClass()) {
          cw.visitInnerClass(className + "$" + t.getName(), className, t.getName(), ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
        }
      }
    }

    generateConstructor(cw);
    cw.visitEnd();
    return cw.toByteArray();
  }

  private void generateValuesForType(@NotNull ClassWriter cw, @NotNull ResourceType resType, @NotNull TObjectIntHashMap<String> cache) {
    Collection<String> resourceNames = myResources.getResourceNames(myNamespace, resType);
    for (String name : resourceNames) {
      int initialValue = myIdProvider.getOrGenerateId(new ResourceReference(myNamespace, resType, name));
      name = IdeResourcesUtil.getFieldNameByResourceName(name);
      generateField(cw, name, initialValue);
      cache.put(name, initialValue);
    }
  }

  /**
   * Returns the list of {@link ResourceReference} to attributes declared in the given styleable resource item.
   */
  @NotNull
  private static List<ResourceReference> getStyleableAttributes(@NotNull ResourceItem item) {
    ResourceValue resourceValue = ApplicationManager.getApplication().runReadAction(
      (Computable<ResourceValue>)() -> item.getResourceValue());
    assert resourceValue instanceof StyleableResourceValue;
    StyleableResourceValue dv = (StyleableResourceValue)resourceValue;
    return Lists.transform(dv.getAllAttributes(), ResourceValue::asReference);
  }

  private void generateStyleable(@NotNull ClassWriter cw, String className) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("generateStyleable(%s)", anonymizeClassName(className)));
    }
    boolean debug = LOG.isDebugEnabled() && isPublicClass(className);

    TObjectIntHashMap<String> indexFieldsCache = myCache.get(ResourceType.STYLEABLE);
    Collection<String> styleableNames = myResources.getResourceNames(myNamespace, ResourceType.STYLEABLE);
    List<MergedStyleable> mergedStyleables = new ArrayList<>(styleableNames.size());

    // Generate all declarations - both int[] and int for the indices into the array.
    for (String styleableName : styleableNames) {
      List<ResourceItem> items = myResources.getResources(myNamespace, ResourceType.STYLEABLE, styleableName);
      if (items.isEmpty()) {
        if (debug) {
          LOG.debug("  No items for " + styleableName);
        }
        continue;
      }
      String fieldName = IdeResourcesUtil.getFieldNameByResourceName(styleableName);
      cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, fieldName, "[I", null, null);
      if (debug) {
        LOG.debug("  Defined styleable " + fieldName);
      }

      // Merge all the styleables with the same name, to compute the sum of all attrs defined in them.
      LinkedHashSet<ResourceReference> mergedAttributes = new LinkedHashSet<>();
      for (ResourceItem item : items) {
        mergedAttributes.addAll(getStyleableAttributes(item));
      }

      mergedStyleables.add(new MergedStyleable(styleableName, mergedAttributes));

      int idx = 0;
      for (ResourceReference attr : mergedAttributes) {
        String styleableEntryName = getResourceName(fieldName, attr);
        int fieldValue = idx++;
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, styleableEntryName, "I", null, fieldValue);
        indexFieldsCache.put(styleableEntryName, fieldValue);
        if (debug) {
          LOG.debug("  Defined styleable " + styleableEntryName);
        }
      }
    }

    // Generate class initializer block to initialize the arrays declared above.
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    for (MergedStyleable mergedStyleable : mergedStyleables) {
      String fieldName = IdeResourcesUtil.getFieldNameByResourceName(mergedStyleable.name);
      TIntArrayList values = new TIntArrayList();
      for (ResourceReference attr : mergedStyleable.attrs) {
        values.add(myIdProvider.getOrGenerateId(attr));
      }
      myStyleableCache.put(fieldName, values);
      generateArrayInitialization(mv, className, fieldName, values);
    }
    mv.visitInsn(RETURN);
    mv.visitMaxs(4, 0);
    mv.visitEnd();
  }

  private static void generateFields(@NotNull final ClassWriter cw, @NotNull TObjectIntHashMap<String> values) {
    values.forEachEntry((name, value) -> {
      generateField(cw, name, value);
      return true;
    });
  }

  private static void generateField(@NotNull ClassWriter cw, String name, int value) {
    cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, name, "I", null, value).visitEnd();
  }

  private void generateIntArraysFromCache(@NotNull ClassWriter cw, String className) {
    // Generate the field declarations.
    for (String name : myStyleableCache.keySet()) {
      cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, name, "[I", null, null);
    }

    // Generate class initializer block to initialize the arrays declared above.
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    myStyleableCache.forEach((arrayName, values) -> {
      generateArrayInitialization(mv, className, arrayName, values);
    });
    mv.visitInsn(RETURN);
    mv.visitMaxs(4, 0);
    mv.visitEnd();
  }

  /**
   * Generates the instruction to push value into the stack. It will select the best opcode depending on the given value.
   */
  private static void pushIntValue(@NotNull MethodVisitor mv, int value) {
    if (value >= -1 && value <= 5) {
      mv.visitInsn(ICONST_0 + value);
    } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
      mv.visitIntInsn(BIPUSH, value);
    } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
      mv.visitIntInsn(SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  /**
   * Generate code to put set the initial values of an array field (for styleables).
   * @param mv the class initializer's MethodVisitor (&lt;clinit&gt;)
   */
  private static void generateArrayInitialization(@NotNull MethodVisitor mv, String className, String fieldName,
                                                  @NotNull TIntArrayList values) {
    if (values.isEmpty()) {
      return;
    }
    pushIntValue(mv, values.size());
    mv.visitIntInsn(NEWARRAY, T_INT);
    for (int idx = 0; idx < values.size(); idx++) {
      mv.visitInsn(DUP);
      pushIntValue(mv, idx);
      mv.visitLdcInsn(values.get(idx));
      mv.visitInsn(IASTORE);
    }
    mv.visitFieldInsn(PUTSTATIC, className, fieldName, "[I");
  }

  /** Generate an empty constructor. */
  private static void generateConstructor(@NotNull ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
  }

  public String getResourceName(String styleableName, @NotNull ResourceReference value) {
    StringBuilder sb = new StringBuilder(30);
    sb.append(styleableName);
    sb.append('_');
    if (!value.getNamespace().equals(myNamespace)) {
      String packageName = value.getNamespace().getPackageName();
      if (packageName != null) {
        appendEscaped(sb, packageName);
        sb.append('_');
      }
    }
    appendEscaped(sb, value.getName());
    return sb.toString();
  }

  private static void appendEscaped(@NotNull StringBuilder sb, @NotNull String v) {
    // See AndroidResourcesIdeUtil.getFieldNameByResourceName
    for (int i = 0, n = v.length(); i < n; i++) {
      char c = v.charAt(i);
      if (c == '.' || c == ':' || c == '-') {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
  }

  private static class MergedStyleable {
    @NotNull final String name;
    @NotNull final LinkedHashSet<ResourceReference> attrs;

    private MergedStyleable(@NotNull String name, @NotNull LinkedHashSet<ResourceReference> attrs) {
      this.name = name;
      this.attrs = attrs;
    }
  }
}
