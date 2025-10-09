@file:JvmName("GradleModuleModelEntityModifications")
package com.android.tools.idea.gradle.project.entities
import com.android.tools.idea.gradle.project.entities.impl.GradleModuleModelEntityImpl
@GeneratedCodeApiVersion(3)
interface GradleModuleModelEntityBuilder: WorkspaceEntityBuilder<GradleModuleModelEntity>{
override var entitySource: EntitySource
var module: ModuleEntityBuilder
var gradleModuleModel: GradleModuleModel
}

internal object GradleModuleModelEntityType : EntityType<GradleModuleModelEntity, GradleModuleModelEntityBuilder>(){
override val entityClass: Class<GradleModuleModelEntity> get() = GradleModuleModelEntity::class.java
override val entityImplBuilderClass: Class<*> get() = GradleModuleModelEntityImpl.Builder::class.java
operator fun invoke(
gradleModuleModel: GradleModuleModel,
entitySource: EntitySource,
init: (GradleModuleModelEntityBuilder.() -> Unit)? = null,
): GradleModuleModelEntityBuilder{
val builder = builder()
builder.gradleModuleModel = gradleModuleModel
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyGradleModuleModelEntity(
entity: GradleModuleModelEntity,
modification: GradleModuleModelEntityBuilder.() -> Unit,
): GradleModuleModelEntity = modifyEntity(GradleModuleModelEntityBuilder::class.java, entity, modification)
var ModuleEntityBuilder.gradleModuleModel: GradleModuleModelEntityBuilder?
by WorkspaceEntity.extensionBuilder(GradleModuleModelEntity::class.java)


@JvmOverloads
@JvmName("createGradleModuleModelEntity")
fun GradleModuleModelEntity(
gradleModuleModel: GradleModuleModel,
entitySource: EntitySource,
init: (GradleModuleModelEntityBuilder.() -> Unit)? = null,
): GradleModuleModelEntityBuilder = GradleModuleModelEntityType(gradleModuleModel, entitySource, init)
