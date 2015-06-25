/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DeclareStyleableResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.google.common.collect.Maps;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

/**
 * The {@linkplain AarResourceClassGenerator} can generate R classes on the fly for a given resource repository.
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
public class AarResourceClassGenerator {

  private Map<ResourceType, TObjectIntHashMap<String>> myCache;
  /** For int[] in styleables. The ints in styleables are stored in {@link #myCache}. */
  private Map<String, List<Integer>> myStyleableCache;
  @NotNull private final AppResourceRepository myAppResources;

  private AarResourceClassGenerator(@NotNull AppResourceRepository appResources) {
    myAppResources = appResources;
  }

  /**
   * Creates a new {@linkplain AarResourceClassGenerator}.
   *
   * @param appResources the application resources used during rendering; this is used to look up dynamic id's
   *                     for resources
   */
  @NotNull
  public static AarResourceClassGenerator create(@NotNull AppResourceRepository appResources) {
    return new AarResourceClassGenerator(appResources);
  }

  /**
   * @param fqcn Fully qualified class name (as accepted by ClassLoader, or as returned by Class.getName())
   */
  @Nullable
  public byte[] generate(String fqcn) {
    String className = fqcn.replace('.', '/');
    ClassWriter cw = new ClassWriter(0);  // Don't compute MAXS and FRAMES.
    cw.visit(V1_6, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, Type.getInternalName(Object.class), null);

    int index = className.lastIndexOf('$');
    if (index != -1) {
      String typeName = className.substring(index + 1);
      ResourceType type = ResourceType.getEnum(typeName);
      if (type == null) {
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
    Collection<String> keys = resType == ResourceType.ID ? myAppResources.getAllIds() : myAppResources.getItemsOfType(resType);
    for (String key : keys) {
      int initialValue = myAppResources.getResourceId(resType, key);
      key = AndroidResourceUtil.getFieldNameByResourceName(key);
      generateField(cw, key, initialValue);
      cache.put(key, initialValue);
    }
  }

  private void generateStyleable(@NotNull ClassWriter cw, @NotNull TObjectIntHashMap<String> styleableIntCache, String className) {
    Collection<String> declaredStyleables = myAppResources.getItemsOfType(ResourceType.DECLARE_STYLEABLE);
    // Generate all declarations - both int[] and int for the indices into the array.
    for (String styleableName : declaredStyleables) {
      List<ResourceItem> items = myAppResources.getResourceItem(ResourceType.DECLARE_STYLEABLE, styleableName);
      if (items == null || items.isEmpty()) {
        continue;
      }
      cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, styleableName, "[I", null, null);
      ResourceValue resourceValue = items.get(0).getResourceValue(false);
      assert resourceValue instanceof DeclareStyleableResourceValue;
      DeclareStyleableResourceValue dv = (DeclareStyleableResourceValue)resourceValue;
      List<AttrResourceValue> attributes = dv.getAllAttributes();
      int idx = 0;
      for (AttrResourceValue value : attributes) {
        Integer initialValue = idx++;
        String styleableEntryName = getResourceName(styleableName, value);
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, styleableEntryName, "I", null, initialValue);
        styleableIntCache.put(styleableEntryName, initialValue);
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
      ResourceValue resourceValue = items.get(0).getResourceValue(false);
      assert resourceValue instanceof DeclareStyleableResourceValue;
      DeclareStyleableResourceValue dv = (DeclareStyleableResourceValue)resourceValue;
      List<AttrResourceValue> attributes = dv.getAllAttributes();
      if (attributes.isEmpty()) {
        continue;
      }
      Integer[] valuesArray = myAppResources.getDeclaredArrayValues(attributes, styleableName);
      if (valuesArray == null) {
        valuesArray = new Integer[attributes.size()];
      }
      List<Integer> values = Arrays.asList(valuesArray);
      myStyleableCache.put(styleableName, values);
      int idx = -1;
      for (AttrResourceValue value : attributes) {
        if (valuesArray[++idx] == null || !value.isFramework()) {
          valuesArray[idx] = myAppResources.getResourceId(ResourceType.ATTR, value.getName());
        }
      }
      generateArrayInitialization(mv, className, styleableName, values);
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
   * Generate code to put set the initial values of an array field (for styleables).
   * @param mv the class initializer's MethodVisitor (&lt;clinit&gt;)
   */
  private static void generateArrayInitialization(@NotNull MethodVisitor mv, String className, String fieldName,
                                                  @NotNull List<Integer> values) {
    if (values.isEmpty()) {
      return;
    }
    mv.visitIntInsn(BIPUSH, values.size());
    mv.visitIntInsn(NEWARRAY, T_INT);
    int idx = 0;
    for (Integer value : values) {
      mv.visitInsn(DUP);
      switch (idx) {
        case 0:
          mv.visitInsn(ICONST_0);
          break;
        case 1:
          mv.visitInsn(ICONST_1);
          break;
        case 2:
          mv.visitInsn(ICONST_2);
          break;
        case 3:
          mv.visitInsn(ICONST_3);
          break;
        case 4:
          mv.visitInsn(ICONST_4);
          break;
        case 5:
          mv.visitInsn(ICONST_5);
          break;
        default:
          mv.visitIntInsn(BIPUSH, idx);
          break;
      }
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
