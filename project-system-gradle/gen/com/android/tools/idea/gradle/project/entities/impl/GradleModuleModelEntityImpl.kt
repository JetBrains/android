@file:OptIn(EntityStorageInstrumentationApi::class)

package com.android.tools.idea.gradle.project.entities.impl   
 
import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleModuleModelEntityImpl(private val dataSource: GradleModuleModelEntityData): GradleModuleModelEntity, WorkspaceEntityBase(dataSource) {

private companion object {
internal val MODULE_CONNECTION_ID: ConnectionId = ConnectionId.create(ModuleEntity::class.java, GradleModuleModelEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)
private val connections = listOf<ConnectionId>(MODULE_CONNECTION_ID)

}

override val module: ModuleEntity
get() = snapshot.instrumentation.getParent(MODULE_CONNECTION_ID, this) as? ModuleEntity ?: error("Parent module not found for GradleModuleModelEntity")           
override val gradleModuleModel: GradleModuleModel
get() {
readField("gradleModuleModel")
return dataSource.gradleModuleModel
}

override val entitySource: EntitySource
get() {
readField("entitySource")
return dataSource.entitySource
}

override fun connectionIdList(): List<ConnectionId> {
return connections
}


internal class Builder(result: GradleModuleModelEntityData?): ModifiableWorkspaceEntityBase<GradleModuleModelEntity, GradleModuleModelEntityData>(result), GradleModuleModelEntityBuilder {
internal constructor(): this(GradleModuleModelEntityData())

override fun applyToBuilder(builder: MutableEntityStorage){
if (this.diff != null){
if (existsInBuilder(builder)){
this.diff = builder
return
}
else{
error("Entity GradleModuleModelEntity is already created in a different builder")
}
}
this.diff = builder
addToBuilder()
this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
this.currentEntityData = null
// Process linked entities that are connected without a builder
processLinkedEntities(builder)
checkInitialization() // TODO uncomment and check failed tests
}

private fun checkInitialization(){
val _diff = diff
if (!getEntityData().isEntitySourceInitialized()){
error("Field WorkspaceEntity#entitySource should be initialized")
}
if (_diff != null){
if (_diff.instrumentation.getParentBuilder(MODULE_CONNECTION_ID, this) == null){
error("Field GradleModuleModelEntity#module should be initialized")
}
}
else{
if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null){
error("Field GradleModuleModelEntity#module should be initialized")
}
}
if (!getEntityData().isGradleModuleModelInitialized()){
error("Field GradleModuleModelEntity#gradleModuleModel should be initialized")
}
}
override fun connectionIdList(): List<ConnectionId>{
return connections
}
// Relabeling code, move information from dataSource to this builder
override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?){
dataSource as GradleModuleModelEntity
if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
if (this.gradleModuleModel != dataSource.gradleModuleModel) this.gradleModuleModel = dataSource.gradleModuleModel
updateChildToParentReferences(parents)
}

        
override var entitySource: EntitySource
get() = getEntityData().entitySource
set(value) {
checkModificationAllowed()
getEntityData(true).entitySource = value
changedProperty.add("entitySource")

}
override var module: ModuleEntityBuilder
get(){
val _diff = diff
return if (_diff != null) {
((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntityBuilder) ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder) ?: error("module is null for GradleModuleModelEntity")
} else {
(this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] as? ModuleEntityBuilder) ?: error("module is null for GradleModuleModelEntity")
}
}
set(value){
checkModificationAllowed()
val _diff = diff
if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null){
if (value is ModifiableWorkspaceEntityBase<*, *>){
value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
_diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
}
if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)){
_diff.instrumentation.addChild(MODULE_CONNECTION_ID, value, this)
}
else{
if (value is ModifiableWorkspaceEntityBase<*, *>){
value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
}
// else you're attaching a new entity to an existing entity that is not modifiable
this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
}
changedProperty.add("module")
}

override var gradleModuleModel: GradleModuleModel
get() = getEntityData().gradleModuleModel
set(value) {
checkModificationAllowed()
getEntityData(true).gradleModuleModel = value
changedProperty.add("gradleModuleModel")

}

override fun getEntityClass(): Class<GradleModuleModelEntity> = GradleModuleModelEntity::class.java
}

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleModuleModelEntityData : WorkspaceEntityData<GradleModuleModelEntity>(){
lateinit var gradleModuleModel: GradleModuleModel

internal fun isGradleModuleModelInitialized(): Boolean = ::gradleModuleModel.isInitialized

override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<GradleModuleModelEntity>{
val modifiable = GradleModuleModelEntityImpl.Builder(null)
modifiable.diff = diff
modifiable.id = createEntityId()
return modifiable
}

override fun createEntity(snapshot: EntityStorageInstrumentation): GradleModuleModelEntity{
val entityId = createEntityId()
return snapshot.initializeEntity(entityId){
val entity = GradleModuleModelEntityImpl(this)
entity.snapshot = snapshot
entity.id = entityId
entity
}
}

override fun getMetadata(): EntityMetadata{
return MetadataStorageImpl.getMetadataByTypeFqn("com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity") as EntityMetadata
}

override fun getEntityInterface(): Class<out WorkspaceEntity>{
return GradleModuleModelEntity::class.java
}

override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*>{
return GradleModuleModelEntity(gradleModuleModel, entitySource){
parents.filterIsInstance<ModuleEntityBuilder>().singleOrNull()?.let { this.module = it }
}
}

override fun getRequiredParents(): List<Class<out WorkspaceEntity>>{
val res = mutableListOf<Class<out WorkspaceEntity>>()
res.add(ModuleEntity::class.java)
return res
}

override fun equals(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as GradleModuleModelEntityData
if (this.entitySource != other.entitySource) return false
if (this.gradleModuleModel != other.gradleModuleModel) return false
return true
}

override fun equalsIgnoringEntitySource(other: Any?): Boolean{
if (other == null) return false
if (this.javaClass != other.javaClass) return false
other as GradleModuleModelEntityData
if (this.gradleModuleModel != other.gradleModuleModel) return false
return true
}

override fun hashCode(): Int{
var result = entitySource.hashCode()
result = 31 * result + gradleModuleModel.hashCode()
return result
}
override fun hashCodeIgnoringEntitySource(): Int{
var result = javaClass.hashCode()
result = 31 * result + gradleModuleModel.hashCode()
return result
}
}
