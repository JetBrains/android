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
import com.android.tools.lint.detector.api.ClassContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;

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
 */
public class AarResourceClassGenerator {
  @NotNull private final LocalResourceRepository myAarResources;
  @NotNull private final AppResourceRepository myAppResources;

  private AarResourceClassGenerator(@NotNull AppResourceRepository appResources, @NotNull LocalResourceRepository aarResources) {
    myAppResources = appResources;
    myAarResources = aarResources;
  }

  /**
   * Creates a new {@linkplain AarResourceClassGenerator}.
   *
   * @param appResources the application resources used during rendering; this is used to look up dynamic id's
   *                     for resources
   * @param aarResources the resource registry for the AAR library
   * @return
   */
  @Nullable
  public static AarResourceClassGenerator create(@NotNull AppResourceRepository appResources,
                                                 @NotNull LocalResourceRepository aarResources) {
    return new AarResourceClassGenerator(appResources, aarResources);
  }

  @SuppressWarnings("deprecation") // Need to handle DeclareStyleableResourceValue
  @Nullable
  public byte[] generate(String fqcn) {
    String className = ClassContext.getInternalName(fqcn);
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V1_6, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, className, null, "java/lang/Object", null);

    int index = className.lastIndexOf('$');
    if (index != -1) {
      String typeName = className.substring(index + 1);
      ResourceType type = ResourceType.getEnum(typeName);
      if (type == null) {
        return null;
      }

      cw.visitInnerClass(className, className.substring(0, index), typeName, ACC_PUBLIC + ACC_FINAL + ACC_STATIC);

      if (type == ResourceType.STYLEABLE) {
        type = ResourceType.DECLARE_STYLEABLE;
        Collection<String> keys = myAarResources.getItemsOfType(type);
        for (String key : keys) {
          List<ResourceItem> items = myAarResources.getResourceItem(type, key);
          if (items == null || items.isEmpty()) {
            continue;
          }
          ResourceItem item = items.get(0);
          cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, key, "[I", null, null);
          ResourceValue resourceValue = item.getResourceValue(false);
          assert resourceValue instanceof DeclareStyleableResourceValue;
          DeclareStyleableResourceValue dv = (DeclareStyleableResourceValue)resourceValue;
          Map<String,AttrResourceValue> attributes = dv.getAllAttributes();
          if (attributes != null) {
            int idx = 0;
            for (AttrResourceValue value : attributes.values()) {
              Integer initialValue = idx++;
              StringBuilder sb = new StringBuilder(30);
              sb.append(key);
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
              cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, sb.toString(), "I", null, initialValue).visitEnd();
            }
          }
        }

        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        for (String key : keys) {
          List<ResourceItem> items = myAarResources.getResourceItem(type, key);
          if (items == null || items.isEmpty()) {
            continue;
          }
          ResourceItem item = items.get(0);
          ResourceValue resourceValue = item.getResourceValue(false);
          assert resourceValue instanceof DeclareStyleableResourceValue;
          DeclareStyleableResourceValue dv = (DeclareStyleableResourceValue)resourceValue;
          Map<String,AttrResourceValue> attributes = dv.getAllAttributes();
          if (attributes == null) {
            continue;
          }

          mv.visitIntInsn(BIPUSH, attributes.size());
          mv.visitIntInsn(NEWARRAY, T_INT);
          int idx = 0;
          for (AttrResourceValue value : attributes.values()) {
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
            Integer initialValue = myAppResources.getResourceId(ResourceType.ATTR, value.getName());
            mv.visitLdcInsn(initialValue);
            mv.visitInsn(IASTORE);
            idx++;
          }
          mv.visitFieldInsn(PUTSTATIC, className, key, "[I");
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 0);
        mv.visitEnd();
      } else {
        Collection<String> keys = myAarResources.getItemsOfType(type);
        for (String key : keys) {
          Integer initialValue = myAppResources.getResourceId(type, key);
          cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC, key, "I",
                        null, initialValue).visitEnd();
        }
      }
    } else {
      // Default R class
      for (ResourceType t : myAarResources.getAvailableResourceTypes()) {
        cw.visitInnerClass(className + "$" + t.getName(), className, t.getName(), ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
      }
    }

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();
    return cw.toByteArray();
  }
}
