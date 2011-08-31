package com.rits.orm

import com.rits.jdbc.Jdbc
import com.rits.orm.drivers.Driver
import scala.collection.mutable.HashMap
import com.rits.jdbc.JdbcMap
import com.rits.orm.exceptions.PersistException
import com.rits.orm.exceptions.QueryException
import com.rits.orm.plugins.OneToManyUpdatePlugin
import com.rits.orm.plugins.PostUpdate
import com.rits.orm.utils.MapOfList
import com.rits.orm.plugins.OneToOneReverseUpdatePlugin
import com.rits.orm.plugins.ManyToManyUpdatePlugin
import com.rits.orm.plugins.DuringUpdate
import com.rits.orm.plugins.ManyToOneUpdatePlugin
import com.rits.orm.plugins.BeforeInsert
import com.rits.orm.plugins.ManyToOneInsertPlugin
import com.rits.orm.plugins.PostInsert
import com.rits.orm.plugins.OneToOneInsertPlugin
import com.rits.orm.plugins.OneToOneReverseInsertPlugin
import com.rits.orm.plugins.OneToManyInsertPlugin
import com.rits.orm.plugins.ManyToManyInsertPlugin

/**
 * @author kostantinos.kougios
 *
 * 13 Jul 2011
 */
final class MapperDao(val driver: Driver) {
	val typeRegistry = driver.typeRegistry
	val typeManager = driver.jdbc.typeManager

	private val postUpdatePlugins = List[PostUpdate](new OneToOneReverseUpdatePlugin(this), new OneToManyUpdatePlugin(this), new ManyToManyUpdatePlugin(this))
	private val duringUpdatePlugins = List[DuringUpdate](new ManyToOneUpdatePlugin(this))
	private val beforeInsertPlugins = List[BeforeInsert](new ManyToOneInsertPlugin(this))
	private val postInsertPlugins = List[PostInsert](new OneToOneInsertPlugin(this), new OneToOneReverseInsertPlugin(this), new OneToManyInsertPlugin(this), new ManyToManyInsertPlugin(this))

	/**
	 * ===================================================================================
	 * Utility methods
	 * ===================================================================================
	 */

	private[orm] def isPersisted(o: Any): Boolean = o.isInstanceOf[Persisted]

	/**
	 * ===================================================================================
	 * CRUD OPERATIONS
	 * ===================================================================================
	 */

	private[orm] def insertInner[PC, T](entity: Entity[PC, T], o: T, entityMap: UpdateEntityMap): T with PC =
		{
			val tpe = typeRegistry.typeOf(entity)
			// if a mock exists in the entity map or already persisted, then return
			// the existing mock/persisted object
			val mock = entityMap.get[PC, T](o)
			if (mock.isDefined) return mock.get

			if (o.isInstanceOf[Persisted]) throw new IllegalArgumentException("can't insert an object that is already persisted: " + o);

			val table = tpe.table

			val modified = ValuesMap.fromEntity(typeManager, tpe, o).toMutableMap
			val modifiedTraversables = new MapOfList[String, Any]

			val UpdateInfo(parent, parentColumnInfo) = entityMap.peek[Persisted, Any, T]

			// extra args for foreign keys
			var extraArgs = if (parent != null) {
				// parent 
				val parentEntity = typeRegistry.entityOfObject[Any, Any](parent)
				val parentColumn = parentColumnInfo.column
				val parentTpe = typeRegistry.typeOf(parentEntity)
				val parentTable = parentTpe.table
				val parentKeysAndValues = parent.valuesMap.toListOfColumnAndValueTuple(parentTable.primaryKeys)
				parentColumn match {
					case otm: OneToMany[_] =>
						val foreignKeyColumns = otm.foreignColumns
						val foreignKeys = parentKeysAndValues.map(_._2)
						if (foreignKeys.size != foreignKeyColumns.size) throw new IllegalArgumentException("mappings of one-to-many from " + parent + " to " + o + " is invalid. Number of FK columns doesn't match primary keys. columns: " + foreignKeyColumns + " , primary key values " + foreignKeys);
						foreignKeyColumns zip foreignKeys
					case oto: OneToOneReverse[T] =>
						oto.foreignColumns zip parentKeysAndValues.map(_._2)
					case _ => Nil
				}
			} else Nil

			// create a mock
			var mockO = tpe.constructor(ValuesMap.fromMutableMap(typeManager, modified ++ modifiedTraversables))
			mockO.mock = true
			entityMap.put(o, mockO)

			extraArgs :::= beforeInsertPlugins.map { plugin =>
				plugin.execute(tpe, o, mockO, entityMap, modified)
			}.flatten
			// arguments
			val args = table.toListOfColumnAndValueTuples(table.simpleTypeNotAutoGeneratedColumns, o) ::: extraArgs

			// insert entity
			if (!args.isEmpty || !table.simpleTypeAutoGeneratedColumns.isEmpty) {
				val ur = driver.doInsert(tpe, args)

				table.simpleTypeAutoGeneratedColumns.foreach { c =>
					modified(c.columnName) = ur.keys.get(c.columnName).get
				}
			}

			// create a more up-to-date mock
			mockO = tpe.constructor(ValuesMap.fromMutableMap(typeManager, modified ++ modifiedTraversables))
			mockO.mock = true
			entityMap.put(o, mockO)

			postInsertPlugins.foreach { plugin =>
				plugin.execute(tpe, o, mockO, entityMap, modified, modifiedTraversables)
			}

			val finalMods = modified ++ modifiedTraversables
			val newE = tpe.constructor(ValuesMap.fromMutableMap(typeManager, finalMods))
			// re-put the actual
			entityMap.put(o, newE)
			newE
		}

	/**
	 * insert an entity into the database
	 */
	def insert[PC, T](entity: Entity[PC, T], o: T): T with PC =
		{
			val entityMap = new UpdateEntityMap
			try {
				val v = insertInner(entity, o, entityMap)
				entityMap.done
				v
			} catch {
				case e => throw new PersistException("An error occured during insert of entity %s with value %s".format(entity, o), e)
			}
		}

	/**
	 * update an entity
	 */

	private[orm] def updateInner[PC, T](entity: Entity[PC, T], o: T, oldValuesMap: ValuesMap, newValuesMap: ValuesMap, entityMap: UpdateEntityMap): T with PC =
		{
			val tpe = typeRegistry.typeOf(entity)
			// if a mock exists in the entity map or already persisted, then return
			// the existing mock/persisted object
			val mock = entityMap.get[PC, T](o)
			if (mock.isDefined) return mock.get

			val table = tpe.table

			val modified = oldValuesMap.toMutableMap ++ newValuesMap.toMutableMap
			val modifiedTraversables = new MapOfList[String, Any]

			def onlyChanged(column: ColumnBase) = newValuesMap(column.alias) != oldValuesMap(column.alias)

			// first, lets update the simple columns that changed

			// run all DuringUpdate plugins
			var pluginArgs = List[(Column, Any)]()
			duringUpdatePlugins.foreach { plugin =>
				pluginArgs :::= plugin.execute(tpe, o, oldValuesMap, newValuesMap, entityMap)
			}
			// find out which simple columns changed
			val columnsChanged = table.simpleTypeNotAutoGeneratedColumns.filter(onlyChanged _)

			// if there is a change, update it
			if (!columnsChanged.isEmpty || !pluginArgs.isEmpty) {
				val args = newValuesMap.toListOfColumnAndValueTuple(columnsChanged) ::: pluginArgs
				val pkArgs = oldValuesMap.toListOfColumnAndValueTuple(table.primaryKeys)
				driver.doUpdate(tpe, args, pkArgs)
			}

			// store a mock in the entity map so that we don't process the same instance twice
			val mockO = tpe.constructor(ValuesMap.fromMutableMap(typeManager, modified ++ modifiedTraversables))
			mockO.mock = true
			entityMap.put(o, mockO)

			postUpdatePlugins.foreach { plugin =>
				plugin.execute(tpe, o, mockO, oldValuesMap, newValuesMap, entityMap, modifiedTraversables)
			}

			// done, construct the updated entity
			val finalValuesMap = ValuesMap.fromMutableMap(typeManager, modified ++ modifiedTraversables)
			tpe.constructor(finalValuesMap)
		}

	/**
	 * update an entity. The entity must have been retrieved from the database and then
	 * changed prior to calling this method.
	 * The whole tree will be updated (if necessary).
	 * The method heavily relies on object equality to assess which entities will be updated.
	 */
	def update[PC, T](entity: Entity[PC, T], o: T with PC): T with PC =
		{
			if (!o.isInstanceOf[Persisted]) throw new IllegalArgumentException("can't update an object that is not persisted: " + o);
			val persisted = o.asInstanceOf[T with PC with Persisted]
			validatePersisted(persisted)
			val entityMap = new UpdateEntityMap
			try {
				val v = updateInner(entity, o, entityMap)
				entityMap.done
				v
			} catch {
				case e: Throwable => throw new PersistException("An error occured during update of entity %s with value %s.".format(entity, o), e)
			}

		}

	private[orm] def updateInner[PC, T](entity: Entity[PC, T], o: T with PC, entityMap: UpdateEntityMap): T with PC =
		{
			val persisted = o.asInstanceOf[T with PC with Persisted]
			val oldValuesMap = persisted.valuesMap
			val newValuesMap = ValuesMap.fromEntity(typeManager, typeRegistry.typeOfObject(o), o)
			updateInner(entity, o, oldValuesMap, newValuesMap, entityMap)
		}
	/**
	 * update an immutable entity. The entity must have been retrieved from the database. Because immutables can't change, a new instance
	 * of the entity must be created with the new values prior to calling this method. Values that didn't change should be copied from o.
	 * The method heavily relies on object equality to assess which entities will be updated.
	 * The whole tree will be updated (if necessary).
	 *
	 * @param	o		the entity, as retrieved from the database
	 * @param	newO	the new instance of the entity with modifications. The database will be updated
	 * 					based on differences between newO and o
	 * @return			The updated entity. Both o and newO should be disposed (not used) after the call.
	 */
	def update[PC, T](entity: Entity[PC, T], o: T with PC, newO: T): T with PC =
		{
			if (!o.isInstanceOf[Persisted]) throw new IllegalArgumentException("can't update an object that is not persisted: " + o);
			val persisted = o.asInstanceOf[Persisted]
			validatePersisted(persisted)
			persisted.discarded = true
			val oldValuesMap = persisted.valuesMap
			val newValuesMap = ValuesMap.fromEntity(typeManager, typeRegistry.typeOfObject(newO), newO)
			val entityMap = new UpdateEntityMap
			try {
				val v = updateInner(entity, newO, oldValuesMap, newValuesMap, entityMap)
				entityMap.done
				v
			} catch {
				case e => throw new PersistException("An error occured during update of entity %s with old value %s and new value %s".format(entity, o, newO), e)
			}

		}

	private def validatePersisted(persisted: Persisted) {
		if (persisted.discarded) throw new IllegalArgumentException("can't operate on an object twice. An object that was updated/deleted must be discarded and replaced by the return value of update(), i.e. onew=update(o) or just be disposed if it was deleted. The offending object was : " + persisted);
		if (persisted.mock) throw new IllegalArgumentException("can't operate on a 'mock' object. Mock objects are created when there are cyclic dependencies of entities, i.e. entity A depends on B and B on A on a many-to-many relationship.  The offending object was : " + persisted);
	}
	/**
	 * select an entity by it's ID
	 *
	 * @param clz		Class[T], classOf[Entity]
	 * @param id		the id
	 * @return			Option[T] or None
	 */
	def select[PC, T](entity: Entity[PC, T], id: Any): Option[T with PC] = select(entity, List(id))
	def select[PC, T](entity: Entity[PC, T], id1: Any, id2: Any): Option[T with PC] = select(entity, List(id1, id2))
	def select[PC, T](entity: Entity[PC, T], id1: Any, id2: Any, id3: Any): Option[T with PC] = select(entity, List(id1, id2, id3))

	def select[PC, T](entity: Entity[PC, T], ids: List[Any]): Option[T with PC] =
		{
			select(entity, ids, new EntityMap)
		}

	private def select[PC, T](entity: Entity[PC, T], ids: List[Any], entities: EntityMap): Option[T with PC] =
		{
			val clz = entity.clz
			val tpe = typeRegistry.typeOf(entity)
			if (tpe.table.primaryKeys.size != ids.size) throw new IllegalStateException("Primary keys number dont match the number of parameters. Primary keys: %s".format(tpe.table.primaryKeys))

			try {
				val om = driver.doSelect(tpe, tpe.table.primaryKeys.map(_.column).zip(ids))
				if (om.isEmpty) None
				else if (om.size > 1) throw new IllegalStateException("expected 1 result for %s and ids %s, but got %d. Is the primary key column a primary key in the table?".format(clz.getSimpleName, ids, om.size))
				else {
					val l = toEntities(om, tpe, entities)
					if (l.size != 1) throw new IllegalStateException("expected 1 object, but got %s".format(l))
					Option(l.head)
				}
			} catch {
				case e => throw new QueryException("An error occured during select of entity %s and primary keys %s".format(entity, ids), e)
			}
		}

	protected[orm] def toEntities[PC, T](lm: List[JdbcMap], tpe: Type[PC, T], entities: EntityMap): List[T with PC] = lm.map { om =>
		val mods = new scala.collection.mutable.HashMap[String, Any]
		mods ++= om.map
		val table = tpe.table
		// calculate the id's for this tpe
		val ids: List[Any] = tpe.table.primaryKeys.map { pk => om(pk.column.columnName) }
		val entity = entities.get[T with PC](tpe.clz, ids)
		if (entity.isDefined) {
			entity.get
		} else {
			def createMock: T with Persisted =
				{
					mods ++= table.oneToManyColumns.map(c => (c.alias -> List()))
					mods ++= table.manyToManyColumns.map(c => (c.alias -> List()))
					// create a mock of the final entity, to avoid cyclic dependencies
					val mock = tpe.constructor(ValuesMap.fromMutableMap(typeManager, mods))
					// mark it as mock
					mock.mock = true
					mock
				}
			// this mock object is updated with any changes that follow
			val mock = createMock
			entities.put(tpe.clz, ids, mock)

			// one to one reverse
			table.oneToOneReverseColumns.foreach { c =>
				val ftpe = typeRegistry.typeOf(c.foreign.clz)
				val fom = driver.doSelect(ftpe, c.foreignColumns.zip(ids))
				val otmL = toEntities(fom, ftpe, entities)
				if (otmL.size != 1) throw new IllegalStateException("expected 1 row but got " + otmL);
				mods(c.foreign.alias) = otmL.head
			}

			// one to one
			table.oneToOneColumns.foreach { c =>
				val ftpe = typeRegistry.typeOf(c.foreign.clz)
				val ftable = ftpe.table
				val foreignKeyValues = c.selfColumns.map(sc => om(sc.columnName))
				val foreignKeys = ftable.primaryKeys zip foreignKeyValues
				val fom = driver.doSelect(ftpe, foreignKeys)
				val otmL = toEntities(fom, ftpe, entities)
				if (otmL.size != 1) throw new IllegalStateException("expected 1 row but got " + otmL);
				mods(c.foreign.alias) = otmL.head
			}
			// many to one
			table.manyToOneColumns.foreach { c =>
				val fe = typeRegistry.entityOf[Any, Any](c.foreign.clz)
				val foreignPKValues = c.columns.map(mtoc => om(mtoc.columnName))
				val fo = entities.get(fe.clz, foreignPKValues)
				val v = if (fo.isDefined) {
					fo.get
				} else {
					select(fe, foreignPKValues, entities).getOrElse(null)
				}
				mods(c.foreign.alias) = v
			}
			// one to many
			table.oneToManyColumns.foreach { c =>
				val ftpe = typeRegistry.typeOf(c.foreign.clz)
				val fom = driver.doSelect(ftpe, c.foreignColumns.zip(ids))
				val otmL = toEntities(fom, ftpe, entities)
				mods(c.foreign.alias) = otmL
			}

			// many to many
			table.manyToManyColumns.foreach { c =>
				val ftpe = typeRegistry.typeOf(c.foreign.clz)
				val fom = driver.doSelectManyToMany(tpe, ftpe, c, c.linkTable.left zip ids)
				val mtmR = toEntities(fom, ftpe, entities)
				mods(c.foreign.alias) = mtmR
			}

			val vm = ValuesMap.fromMutableMap(typeManager, mods)
			mock.valuesMap.m = vm.m
			val entity = tpe.constructor(vm)
			entities.reput(tpe.clz, ids, entity)
			entity
		}
	}

	/**
	 * deletes an entity from the database
	 */
	def delete[PC, T](entity: Entity[PC, T], o: T with PC): T =
		{
			if (!o.isInstanceOf[Persisted]) throw new IllegalArgumentException("can't delete an object that is not persisted: " + o);

			val persisted = o.asInstanceOf[Persisted]
			if (persisted.discarded) throw new IllegalArgumentException("can't operate on an object twice. An object that was updated/deleted must be discarded and replaced by the return value of update(), i.e. onew=update(o) or just be disposed if it was deleted. The offending object was : " + o);
			persisted.discarded = true

			val tpe = typeRegistry.typeOf(entity)
			val table = tpe.table

			try {
				val keyValues = table.toListOfPrimaryKeyAndValueTuples(o)
				driver.doDelete(tpe, keyValues)
				o
			} catch {
				case e => throw new PersistException("An error occured during delete of entity %s with value %s".format(entity, o), e)
			}

		}
	/**
	 * ===================================================================================
	 * ID helper methods
	 * ===================================================================================
	 */
	/**
	 * retrieve the id of an entity
	 */
	def intIdOf(o: AnyRef) = o match {
		case iid: IntId => iid.id
	}

	/**
	 * retrive the id of an entity
	 */
	def longIdOf(o: AnyRef) = o match {
		case iid: LongId => iid.id
	}

	/**
	 * ===================================================================================
	 * common methods
	 * ===================================================================================
	 */
	override def toString = "MapperDao(%s)".format(driver)
}
