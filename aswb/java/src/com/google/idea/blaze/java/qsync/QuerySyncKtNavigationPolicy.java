
/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.qsync;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.CachedValueProvider.Result;
import java.util.HashMap;
import java.util.Map;
import kotlin.sequences.Sequence;
import kotlin.streams.jdk8.StreamsKt;
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile;
import org.jetbrains.kotlin.idea.navigation.KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;
public class QuerySyncKtNavigationPolicy
  extends KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl {
  private static final ThreadLocal<Map<ClassId, KtClsFile>> localCache = ThreadLocal.withInitial(HashMap::new);

  @Override
  public KtElement getNavigationElement(KtDeclaration ktDeclaration) {
    Map<ClassId, KtClsFile> classIdToKtClsFile = localCache.get();
    ClassId classId = null;
    try {
      PsiFile psiFile = ktDeclaration.getContainingFile();
      if (psiFile instanceof KtClsFile ktClsFile) {
        // Since getClassesByClassId does list ktDeclaration/ ktDeclaration's containing file as
        // parameter. We need a way to temporarily store the mapping between classId and the compiled
        // file that it's in.
        if (ktDeclaration instanceof KtClassLikeDeclaration ktClassLikeDeclaration) {
          classId = ktClassLikeDeclaration.getClassId();
        } else {
          KtClassOrObject ktClassOrObject = KtPsiUtilKt.getContainingClassOrObject(ktDeclaration);
          if (ktClassOrObject != null) {
            classId = ktClassOrObject.getClassId();
          }
        }
        if (classId != null) {
          classIdToKtClsFile.put(classId, ktClsFile);
        }
      }
      return super.getNavigationElement(ktDeclaration);
    } finally {
      if (classId != null) {
        classIdToKtClsFile.remove(classId);
      }
      if (classIdToKtClsFile.isEmpty()) {
        localCache.remove();
      }
    }
  }
  
  @Override
  public Sequence<KtClassOrObject> getClassesByClassId(
    ClassId classId, Project project, Scope scope) {
    KtClsFile ktClsFile = localCache.get().get(classId);
    if (ktClsFile == null) {
      return super.getClassesByClassId(classId, project, scope);
    }
    PsiFile psiFile = getSourceFile(ktClsFile, project);
    if (psiFile != null && psiFile instanceof KtFile ktFile) {
      return StreamsKt.asSequence(
        PsiTreeUtil.findChildrenOfType(ktFile, KtClassOrObject.class).stream()
          .filter(d -> classId.asSingleFqName().equals(d.getFqName())));
    }
    return super.getClassesByClassId(classId, project, scope);
  }
  private PsiFile getSourceFile(KtClsFile ktClsFile, Project project) {
    return CachedValuesManager.getCachedValue(
      ktClsFile,
      () ->
        Result.create(
          new ClassFileKtSourceFinder(ktClsFile).findSourceFile(),
          ktClsFile,
          QuerySyncManager.getInstance(project).getProjectModificationTracker()));
  }
}
