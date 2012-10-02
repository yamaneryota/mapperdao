package com.googlecode.mapperdao

import com.googlecode.mapperdao.events.Events

/**
 * The MapperDao is the central trait that allows CRUD operations on entities.
 *
 * insert, update, delete and select's can be performed and all these methods
 * require the entity as a parameter and optionally a configuration for the
 * operation..
 *
 * @author kostantinos.kougios
 */
trait MapperDao {
	// default configurations, can be overriden
	protected val defaultSelectConfig = SelectConfig.default
	protected val defaultDeleteConfig = DeleteConfig.default
	protected val defaultUpdateConfig = UpdateConfig(deleteConfig = defaultDeleteConfig)

	/**
	 * insert an entity into the database. The entity and all related non-persisted entities
	 * will be inserted into the database. All related persisted entities will be updated
	 * if their state changed.
	 *
	 * @param	entity		the entity, i.e. ProductEntity
	 * @param	o			the value, i.e. a Product
	 * @return	T with PC, i.e. Product with IntId which contains any autogenerated keys.
	 */
	def insert[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T): T with PC = insert(defaultUpdateConfig, entity, o)
	/**
	 * Will insert the entity into the database and will use the UpdateConfig to decide
	 * which related entities will be inserted, deleted etc.
	 *
	 * @see 	#UpdateConfig for configuration documentation.
	 * @see		#insert(entity,o)
	 */
	def insert[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T): T with PC

	/**
	 * updates a mutable entity. Non-persisted related entities will be inserted and persisted
	 * related entities will be updated (if their state changed).
	 *
	 * @param	entity	The entity, i.e. ProductEntity
	 * @param	o		the modified value, T with PC (persisted). I.e. Product with IntId
	 *
	 * @return	the updated entity, matching o, with type T with PC (hence it will include
	 * 			any autogenerated primary keys). I.e. Product with IntId which is equal to o
	 */
	def update[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T with PC): T with PC = update(defaultUpdateConfig, entity, o)

	/**
	 * configurable update of a mutable entity
	 *
	 * @see 	#UpdateConfig for configuration documentation.
	 * @see		#update(entity,o)
	 */
	def update[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC): T with PC

	/**
	 * update of an immutable entity.
	 *
	 * @param	entity	The entity, i.e. ProductEntity
	 * @param	o		the old value, T with PC (persisted). I.e. Product with IntId
	 * @param	newO	the new value, T. I.e. Product
	 *
	 * @return	the updated entity, matching T but with type T with PC (hence it will include
	 * 			any autogenerated primary keys). I.e. Product with IntId which is equal to newO
	 */
	def update[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T with PC, newO: T): T with PC =
		update(defaultUpdateConfig, entity, o, newO)

	/**
	 * configurable update of immutable entities. Similar to update(entity,o,newO)
	 * but updateConfig configures the update.
	 *
	 * @see 	#UpdateConfig for configuration documentation.
	 * @see		#update(entity,o,newO)
	 */
	def update[ID, PC <: DeclaredIds[ID], T](updateConfig: UpdateConfig, entity: Entity[ID, PC, T], o: T with PC, newO: T): T with PC

	def merge[ID, PC <: DeclaredIds[ID], T](
		entity: Entity[ID, PC, T],
		o: T,
		id: ID): T with PC = merge(defaultSelectConfig, defaultUpdateConfig, entity, o, id)

	def merge[ID, PC <: DeclaredIds[ID], T](
		selectConfig: SelectConfig,
		updateConfig: UpdateConfig,
		entity: Entity[ID, PC, T],
		o: T,
		id: ID): T with PC

	/**
	 * select an entity by it's ID
	 *
	 * @param entity	the entity, i.e. ProductEntity
	 * @param id		the id that will be fetched
	 * @return			Option[T with PC] or None, i.e. Some(Product with IntId) if the id
	 * 					exists.
	 */
	def select[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], id: ID): Option[T with PC] = select(defaultSelectConfig, entity, id)

	/**
	 * select an entity with configuration of what will be loaded, lazy loaded, caching etc..
	 * i.e.
	 * SelectConfig(skip=Set(ProductEntity.attributes)) // attributes won't be loaded
	 *
	 * @param	selectConfig	the configuration for this select
	 * @param	entity			the entity to load i.e. ProductEntity
	 * @param	id				the id of the entity
	 * @return	Option[T with PC] i.e. Some(Product with IntId)
	 *
	 * @see 	#SelectConfig for all available configuration parameters
	 */
	def select[ID, PC <: DeclaredIds[ID], T](selectConfig: SelectConfig, entity: Entity[ID, PC, T], id: ID): Option[T with PC]

	/**
	 * deletes an entity from the database. By default, related entities won't be deleted.
	 * It is assumed that the delete will cascade appropriatelly.
	 * Please use delete(deleteConfig, entity, o) to fine tune the operation.
	 *
	 * @param	entity		the entity to be deleted, i.e. ProductEntity
	 * @param	o			the value of the entity that will be deleted, T with PC, i.e. a Product.
	 * 						o should have been retrieved from the database.
	 * @return	the value of the entity unlinked from the database.
	 */
	def delete[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T with PC): T =
		delete(defaultDeleteConfig, entity, o)

	/**
	 * configurable delete of an entity. This allows fine tuned deletion of the entity
	 * and all related data.
	 *
	 * @param	deleteConfig	the configuration, please see #DeleteConfig
	 * @param	entity			the entity to be deleted, i.e. ProductEntity
	 * @param	o				the value of the entity that will be deleted, T with PC, i.e. a Product.
	 * 							o should have been retrieved from the database.
	 * @return	the value of the entity unlinked from the database.
	 */
	def delete[ID, PC <: DeclaredIds[ID], T](deleteConfig: DeleteConfig, entity: Entity[ID, PC, T], o: T with PC): T

	/**
	 * this will delete an entity based on it's id.
	 *
	 * The delete will cascade to related entities only if there are cascade constraints
	 * on the foreign keys in the database. In order to configure mapperdao to delete
	 * related entities, select() the entity first and then delete it using
	 * delete(deleteConfig, entity, o). (In any case to do the same at the database level,
	 * queries would be required in order to delete the related data)
	 */
	def delete[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], id: ID): Unit

	/**
	 * unlinks an entity from mapperdao. The entity is not tracked for changes and can't
	 * be used in updates or deletes. The extra memory used by mapperdao is released.
	 *
	 * Use this i.e. when you want to store the entity in a session.
	 */
	def unlink[ID, PC <: DeclaredIds[ID], T](entity: Entity[ID, PC, T], o: T): T = throw new IllegalStateException("Not supported")
}