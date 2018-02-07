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

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.android.util.AndroidResourceUtil;
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
 * The R class for an aar should contain the resource references to resources from the aar and all its
 * dependencies. It is not straight-forward to get the list of dependencies after the creation of the resource
 * repositories for each aar. So, we use the app's resource repository and generate the R file from it. This
 * will break custom libraries that use reflection on the R class, but meh.
 */
public class ResourceClassGenerator {
  private static final Logger LOG = Logger.getInstance(ResourceClassGenerator.class);

  private Map<ResourceType, TObjectIntHashMap<String>> myCache;
  /** For int[] in styleables. The ints in styleables are stored in {@link #myCache}. */
  private Map<String, List<Integer>> myStyleableCache;
  @NotNull private final AppResourceRepository myAppResources;

  private ResourceClassGenerator(@NotNull AppResourceRepository appResources) {
    myAppResources = appResources;
  }

  /**
   * Creates a new {@linkplain ResourceClassGenerator}.
   *
   * @param appResources the application resources used during rendering; this is used to look up dynamic id's
   *                     for resources
   */
  @NotNull
  public static ResourceClassGenerator create(@NotNull AppResourceRepository appResources) {
    return new ResourceClassGenerator(appResources);
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
      ResourceType type = ResourceType.getEnum(typeName);
      if (type == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("  type '%s' doesn't exist", typeName));
        }
        return null;
      }

      cw.visitInnerClass(className, className.substring(0, index), typeName, ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
      if (myCache == null) {
        myCache = Maps.newHashMap();
      }
      if (type == ResourceType.STYLEABLE) {
        if (myStyleableCache == null) {
          TObjectIntHashMap<String> styleableIntCache = new TObjectIntHashMap<String>();
          myCache.put(type, styleableIntCache);
          myStyleableCache = Maps.newHashMap();
          generateStyleable(cw, styleableIntCache, className);
        }
        else {
          TObjectIntHashMap<String> styleableIntCache = myCache.get(type);
          assert styleableIntCache != null;
          generateFields(cw, styleableIntCache);
          generateIntArrayFromCache(cw, className, myStyleableCache);
        }
      } else {
        TObjectIntHashMap<String> typeCache = myCache.get(type);
        if (typeCache == null) {
          typeCache = new TObjectIntHashMap<String>();
          myCache.put(type, typeCache);
          generateValuesForType(cw, type, typeCache);
        }
        else {
          generateFields(cw, typeCache);
        }
      }
    } else {
      // Default R class.
      boolean styleableAdded = false;
      for (ResourceType t : myAppResources.getAvailableResourceTypes()) {
        // getAvailableResourceTypes() sometimes returns both styleable and declare styleable. Make sure that we only create one subclass.
        if (t == ResourceType.DECLARE_STYLEABLE) {
          t = ResourceType.STYLEABLE;
        }
        if (t == ResourceType.STYLEABLE) {
          if (styleableAdded) {
            continue;
          } else {
            styleableAdded = true;
          }
        }
        cw.visitInnerClass(className + "$" + t.getName(), className, t.getName(), ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
      }
    }

    generateConstructor(cw);
    cw.visitEnd();
    return cw.toByteArray();
  }

  private void generateValuesForType(@NotNull ClassWriter cw, @NotNull ResourceType resType, @NotNull TObjectIntHashMap<String> cache) {
    Collection<String> keys = myAppResources.getItemsOfType(resType);
    for (String key : keys) {
      int initialValue = myAppResources.getResourceId(resType, key);
      key = AndroidResourceUtil.getFieldNameByResourceName(key);
      generateField(cw, key, initialValue);
      cache.put(key, initialValue);
    }
  }

  /**
   * Returns the list of {@link AttrResourceValue} attributes declared in the given styleable resource item.
   */
  @NotNull
  private static List<AttrResourceValue> getStyleableAttributes(@NotNull ResourceItem item) {
    ResourceValue resourceValue = ApplicationManager.getApplication().runReadAction(
      (Computable<ResourceValue>)() -> item.getResourceValue());
    assert resourceValue instanceof DeclareStyleableResourceValue;
    DeclareStyleableResourceValue dv = (DeclareStyleableResourceValue)resourceValue;
    return dv.getAllAttributes();
  }

  private void generateStyleable(@NotNull ClassWriter cw, @NotNull TObjectIntHashMap<String> styleableIntCache, String className) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("generateStyleable(%s)", anonymizeClassName(className)));
    }

    boolean debug = LOG.isDebugEnabled() && isPublicClass(className);
    Collection<String> declaredStyleables = myAppResources.getItemsOfType(ResourceType.DECLARE_STYLEABLE);
    // Generate all declarations - both int[] and int for the indices into the array.
    for (String styleableName : declaredStyleables) {
      List<ResourceItem> items = myAppResources.getResourceItem(ResourceType.DECLARE_STYLEABLE, styleableName);
      if (items == null || items.isEmpty()) {
        if (debug) {
          LOG.debug("  No items for " + styleableName);
        }
        continue;
      }
      String fieldName = AndroidResourceUtil.getFieldNameByResourceName(styleableName);
      cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, fieldName, "[I", null, null);
      if (debug) {
        LOG.debug("  Defined styleable " + fieldName);
      }

      // Merge all the styleables with the same name
      List<AttrResourceValue> mergedAttributes = new ArrayList<>();
      for (ResourceItem item : items) {
        mergedAttributes.addAll(getStyleableAttributes(item));
      }

      int idx = 0;
      HashSet<String> styleablesEntries = new HashSet<>();
      for (AttrResourceValue value : mergedAttributes) {
        String styleableEntryName = getResourceName(fieldName, value);
        // Because we are merging styleables from multiple sources, we could have duplicates
        if (!styleablesEntries.add(styleableEntryName)) {
          continue;
        }

        Integer initialValue = idx++;
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, styleableEntryName, "I", null, initialValue);
        styleableIntCache.put(styleableEntryName, initialValue);
        if (debug) {
          LOG.debug("  Defined styleable " + styleableEntryName);
        }
      }
    }

    // Generate class initializer block to initialize the arrays declared above.
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    for (String styleableName : declaredStyleables) {
      List<ResourceItem> items = myAppResources.getResourceItem(ResourceType.DECLARE_STYLEABLE, styleableName);
      if (items == null || items.isEmpty()) {
        continue;
      }

      List<Integer> values = new ArrayList<>();
      List<AttrResourceValue> mergedAttributes = new ArrayList<>();
      String fieldName = AndroidResourceUtil.getFieldNameByResourceName(styleableName);
      myStyleableCache.put(fieldName, values);
      for (ResourceItem item : items) {
        List<AttrResourceValue> attributes = getStyleableAttributes(item);
        if (attributes.isEmpty()) {
          continue;
        }
        mergedAttributes.addAll(attributes);
        Integer[] valuesArray = myAppResources.getDeclaredArrayValues(attributes, styleableName);
        if (valuesArray == null) {
          valuesArray = new Integer[attributes.size()];
        }
        Collections.addAll(values, valuesArray);
      }

      HashSet<String> styleableEntries = new HashSet<>();
      int idx = -1;
      for (AttrResourceValue value : mergedAttributes) {
        idx++;
        if (values.get(idx) == null || !value.isFramework()) {
          String name = value.getName();
          if (!styleableEntries.add(name)) {
            // This is a duplicate, remove
            values.remove(idx);
            idx--;
          }
          else {
            values.set(idx, myAppResources.getResourceId(ResourceType.ATTR, name));
          }
        }
      }
      generateArrayInitialization(mv, className, fieldName, values);
    }
    mv.visitInsn(RETURN);
    mv.visitMaxs(4, 0);
    mv.visitEnd();
  }

  private static void generateFields(@NotNull final ClassWriter cw, @NotNull TObjectIntHashMap<String> values) {
    values.forEachEntry(new TObjectIntProcedure<String>() {
      @Override
      public boolean execute(String name, int value) {
        generateField(cw, name, value);
        return true;
      }
    });
  }

  private static void generateField(@NotNull ClassWriter cw, String name, int value) {
    cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, name, "I", null, value).visitEnd();
  }

  private static void generateIntArrayFromCache(@NotNull ClassWriter cw, String className, Map<String, List<Integer>> styleableCache) {
    // Generate the field declarations.
    for (String name : styleableCache.keySet()) {
      cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, name, "[I", null, null);
    }

    // Generate class initializer block to initialize the arrays declared above.
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    for (Map.Entry<String, List<Integer>> entry : styleableCache.entrySet()) {
      List<Integer> values = entry.getValue();
      if (!values.isEmpty()) {
        generateArrayInitialization(mv, className, entry.getKey(), values);
      }
    }
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
                                                  @NotNull List<Integer> values) {
    if (values.isEmpty()) {
      return;
    }
    pushIntValue(mv, values.size());
    mv.visitIntInsn(NEWARRAY, T_INT);
    int idx = 0;
    for (Integer value : values) {
      mv.visitInsn(DUP);
      pushIntValue(mv, idx);
      mv.visitLdcInsn(value);
      mv.visitInsn(IASTORE);
      idx++;
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

  public static String getResourceName(String styleableName, @NotNull AttrResourceValue value) {
    StringBuilder sb = new StringBuilder(30);
    sb.append(styleableName);
    sb.append('_');
    if (value.isFramework()) {
      sb.append("android_");
    }
    String v = value.getName();
    // See AndroidResourceUtil.getFieldNameByResourceName
    for (int i = 0, n = v.length(); i < n; i++) {
      char c = v.charAt(i);
      if (c == '.' || c == ':' || c == '-') {
        sb.append('_');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
