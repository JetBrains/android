@file:JvmName("GradleAndroidModelEntityModifications")
package com.android.tools.idea.gradle.project.entities
import com.android.tools.idea.gradle.project.entities.impl.GradleAndroidModelEntityImpl
@GeneratedCodeApiVersion(3)
interface GradleAndroidModelEntityBuilder: WorkspaceEntityBuilder<GradleAndroidModelEntity>{
override var entitySource: EntitySource
var module: ModuleEntityBuilder
var gradleAndroidModel: GradleAndroidModelImpl
var resolvedVariant: IdeVariantImpl?
}

internal object GradleAndroidModelEntityType : EntityType<GradleAndroidModelEntity, GradleAndroidModelEntityBuilder>(){
override val entityClass: Class<GradleAndroidModelEntity> get() = GradleAndroidModelEntity::class.java
override val entityImplBuilderClass: Class<*> get() = GradleAndroidModelEntityImpl.Builder::class.java
operator fun invoke(
gradleAndroidModel: GradleAndroidModelImpl,
entitySource: EntitySource,
init: (GradleAndroidModelEntityBuilder.() -> Unit)? = null,
): GradleAndroidModelEntityBuilder{
val builder = builder()
builder.gradleAndroidModel = gradleAndroidModel
builder.entitySource = entitySource
init?.invoke(builder)
return builder
}
}

fun MutableEntityStorage.modifyGradleAndroidModelEntity(
entity: GradleAndroidModelEntity,
modification: GradleAndroidModelEntityBuilder.() -> Unit,
): GradleAndroidModelEntity = modifyEntity(GradleAndroidModelEntityBuilder::class.java, entity, modification)
var ModuleEntityBuilder.gradleAndroidModel: GradleAndroidModelEntityBuilder?
by WorkspaceEntity.extensionBuilder(GradleAndroidModelEntity::class.java)


@JvmOverloads
@JvmName("createGradleAndroidModelEntity")
fun GradleAndroidModelEntity(
gradleAndroidModel: GradleAndroidModelImpl,
entitySource: EntitySource,
init: (GradleAndroidModelEntityBuilder.() -> Unit)? = null,
): GradleAndroidModelEntityBuilder = GradleAndroidModelEntityType(gradleAndroidModel, entitySource, init)
