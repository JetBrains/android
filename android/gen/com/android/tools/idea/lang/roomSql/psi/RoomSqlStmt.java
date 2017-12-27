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

// ATTENTION: This file has been automatically generated from roomSql.bnf. Do not edit it manually.

package com.android.tools.idea.lang.roomSql.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RoomSqlStmt extends PsiElement {

  @Nullable
  RoomAlterTableStmt getAlterTableStmt();

  @Nullable
  RoomAnalyzeStmt getAnalyzeStmt();

  @Nullable
  RoomAttachStmt getAttachStmt();

  @Nullable
  RoomBeginStmt getBeginStmt();

  @Nullable
  RoomCommitStmt getCommitStmt();

  @Nullable
  RoomCreateIndexStmt getCreateIndexStmt();

  @Nullable
  RoomCreateTableStmt getCreateTableStmt();

  @Nullable
  RoomCreateTriggerStmt getCreateTriggerStmt();

  @Nullable
  RoomCreateViewStmt getCreateViewStmt();

  @Nullable
  RoomCreateVirtualTableStmt getCreateVirtualTableStmt();

  @Nullable
  RoomDeleteStmt getDeleteStmt();

  @Nullable
  RoomDeleteStmtLimited getDeleteStmtLimited();

  @Nullable
  RoomDetachStmt getDetachStmt();

  @Nullable
  RoomDropIndexStmt getDropIndexStmt();

  @Nullable
  RoomDropTableStmt getDropTableStmt();

  @Nullable
  RoomDropTriggerStmt getDropTriggerStmt();

  @Nullable
  RoomDropViewStmt getDropViewStmt();

  @Nullable
  RoomInsertStmt getInsertStmt();

  @Nullable
  RoomPragmaStmt getPragmaStmt();

  @Nullable
  RoomReindexStmt getReindexStmt();

  @Nullable
  RoomReleaseStmt getReleaseStmt();

  @Nullable
  RoomRollbackStmt getRollbackStmt();

  @Nullable
  RoomSavepointStmt getSavepointStmt();

  @Nullable
  RoomSelectStmt getSelectStmt();

  @Nullable
  RoomUpdateStmt getUpdateStmt();

  @Nullable
  RoomUpdateStmtLimited getUpdateStmtLimited();

  @Nullable
  RoomVacuumStmt getVacuumStmt();

}
