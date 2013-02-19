package com.googlecode.mapperdao.state.persistcmds

import com.googlecode.mapperdao._
import utils.TraversableSeparation

/**
 * entities are converted to PersistOps
 *
 * @author kostantinos.kougios
 *
 *         21 Nov 2012
 */
class CmdPhase(typeManager: TypeManager) {

	private var alreadyProcessed = Map[Int, List[PersistCmd]]()

	def toInsertCmd[ID, T](
		tpe: Type[ID, T],
		newVM: ValuesMap,
		updateConfig: UpdateConfig
		) = insert(tpe, newVM, true, updateConfig)

	def toUpdateCmd[ID, T](
		tpe: Type[ID, T],
		oldValuesMap: ValuesMap,
		newValuesMap: ValuesMap,
		updateConfig: UpdateConfig
		) = update(tpe, oldValuesMap, newValuesMap, true, updateConfig)

	private def insert[ID, T](
		tpe: Type[ID, T],
		newVM: ValuesMap,
		mainEntity: Boolean,
		updateConfig: UpdateConfig
		): List[PersistCmd] = {
		alreadyProcessed.get(newVM.identity) match {
			case None =>
				val table = tpe.table
				val columnAndValues = newVM.toListOfSimpleColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
				// make sure we don't process it again due to deep tree's
				alreadyProcessed += (newVM.identity -> Nil)

				val op = InsertCmd(tpe, newVM, columnAndValues, mainEntity) :: related(tpe, None, newVM, updateConfig)

				alreadyProcessed += (newVM.identity -> op)
				op
			case Some(x) =>
				Nil
		}
	}

	private def update[ID, T](
		tpe: Type[ID, T],
		oldVM: ValuesMap,
		newVM: ValuesMap,
		mainEntity: Boolean,
		updateConfig: UpdateConfig
		): List[PersistCmd] = {
		val op = alreadyProcessed.get(newVM.identity)
		if (op.isDefined) {
			Nil
		} else {
			val table = tpe.table
			val newColumnAndValues = newVM.toListOfColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
			val oldColumnAndValues = oldVM.toListOfColumnAndValueTuple(table.simpleTypeNotAutoGeneratedColumns)
			val changedColumnAndValues = (newColumnAndValues zip oldColumnAndValues) collect {
				case ((nc, nv), (oc, ov)) if (nv != ov) => (nc, nv)
			}
			// make sure we don't process it again due to deep tree's
			alreadyProcessed += (newVM.identity -> Nil)

			val rel = related(tpe, Some(oldVM), newVM, updateConfig)
			val op = UpdateCmd(tpe, oldVM, newVM, changedColumnAndValues, mainEntity) :: rel

			alreadyProcessed += (newVM.identity -> op)
			op
		}
	}

	/**
	 * ---------------------------------------------------------------------------------------------
	 * A big block of match statements. I think it is easier this way, till I find a way to
	 * split them.
	 * ---------------------------------------------------------------------------------------------
	 */
	private def related[ID, T](
		tpe: Type[ID, T],
		oldVMO: Option[ValuesMap],
		newVM: ValuesMap,
		updateConfig: UpdateConfig
		): List[PersistCmd] = {
		tpe.table.relationshipColumnInfos(updateConfig.skip).map {
			/**
			 * ---------------------------------------------------------------------------------------------
			 * Many-To-Many
			 * ---------------------------------------------------------------------------------------------
			 */
			case ci@ColumnInfoTraversableManyToMany(column, columnToValue, _) =>
				val foreignEntity = column.foreign.entity
				foreignEntity match {
					/**
					 * ---------------------------------------------------------------------------------------------
					 * Many-To-Many : External entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEE: ExternalEntity[_, _] =>
						if (oldVMO.isDefined) {
							// entity is updated
							val oldVM = oldVMO.get
							val oldT = oldVM.manyToMany(column)
							val newT = newVM.manyToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEntity, oldT, newT)

							UpdateExternalManyToManyCmd(tpe, newVM, foreignEE, ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]], added, intersect, removed) :: Nil
						} else {
							val added = newVM.manyToMany(column)
							UpdateExternalManyToManyCmd(tpe, newVM, foreignEE, ci.asInstanceOf[ColumnInfoTraversableManyToMany[T, Any, Any]], added, Nil, Nil) :: Nil
						}

					/**
					 * ---------------------------------------------------------------------------------------------
					 * Many-To-Many : Normal entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case _ =>
						if (oldVMO.isDefined) {
							// entity is updated
							val oldVM = oldVMO.get
							val oldT = oldVM.manyToMany(column)
							val newT = newVM.manyToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEntity, oldT, newT)

							val addedCmds = added.toList.map {
								fo =>
									val foCmds = insertOrUpdate(foreignEntity.tpe, fo, updateConfig)
									val foreignVM = findVM(fo)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: foCmds
							}.flatten
							val removedCms = removed.toList.map {
								fo =>
									val foreignVM = vmFor(foreignEntity.tpe, fo)
									DeleteManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										oldVM,
										foreignVM
									)
							}

							val intersectCmds = intersect.toList.map {
								case (oldO, newO) =>
									val oVM = oldO match {
										case p: Persisted => p.mapperDaoValuesMap
									}
									val nVM = vmFor(foreignEntity.tpe, newO)
									update(foreignEntity.tpe, oVM, nVM, false, updateConfig)
							}.flatten
							addedCmds ::: removedCms ::: intersectCmds
						} else {
							// entity is new
							newVM.manyToMany(column).map {
								case p: DeclaredIds[Any] =>
									// we need to link to the already existing foreign entity
									// and update the foreign entity
									val foreignVM = vmFor(foreignEntity.tpe, p)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: doUpdate(foreignEntity.tpe, p, updateConfig)
								case o =>
									// we need to insert the foreign entity and link to entity
									val foreignVM = vmFor(foreignEntity.tpe, o) //ValuesMap.fromType(typeManager, foreignEntity.tpe, o)
									InsertManyToManyCmd(
										tpe,
										foreignEntity.tpe,
										column,
										newVM,
										foreignVM) :: insert(foreignEntity.tpe, foreignVM, false, updateConfig)
							}.flatten
						}
				}

			/**
			 * ---------------------------------------------------------------------------------------------
			 * Many-To-One
			 * ---------------------------------------------------------------------------------------------
			 */
			case ci@ColumnInfoManyToOne(column, columnToValue, _) =>
				val fo = newVM.manyToOne(column)
				column.foreign.entity match {
					/**
					 * ---------------------------------------------------------------------------------------------
					 * External Entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEE: ExternalEntity[_, _] =>
						// insert/update
						val foreignTpe = foreignEE.tpe
						val v = foreignTpe.table.toListOfPrimaryKeyValues(fo)
						(
							ExternalEntityRelatedCmd(
								if (fo != null) System.identityHashCode(fo) else 0,
								column,
								newVM,
								oldVMO,
								foreignTpe,
								v
							)
								:: UpdateExternalManyToOneCmd(foreignEE, ci, newVM, fo)
								:: Nil
							)

					/**
					 * ---------------------------------------------------------------------------------------------
					 * Normal Entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEntity =>
						val foreignTpe = foreignEntity.tpe
						val oldFoVMO = oldVMOf(oldVMO.map(_.manyToOne(column)))
						if (fo == null) {
							EntityRelatedCmd(0, column, newVM, oldVMO, foreignTpe, null, oldFoVMO, false) :: Nil
						} else {
							// insert new
							val foreignVM = vmFor(foreignTpe, fo)

							(
								DependsCmd(newVM.identity, foreignVM.identity)
									:: EntityRelatedCmd(foreignVM.identity, column, newVM, oldVMO, foreignTpe, foreignVM, oldFoVMO, false)
									:: (fo match {
									case p: DeclaredIds[_] =>
										doUpdate(foreignTpe.asInstanceOf[Type[Any, Any]], p.asInstanceOf[Any with DeclaredIds[Any]], updateConfig)
									case _ =>
										// we need to insert the foreign entity and link to entity
										insert(foreignTpe, foreignVM, false, updateConfig)
								})
								)
						}
				}

			/**
			 * ---------------------------------------------------------------------------------------------
			 * One-To-Many
			 * ---------------------------------------------------------------------------------------------
			 */
			case ci@ColumnInfoTraversableOneToMany(column, columnToValue, _, entityOfT) =>
				column.foreign.entity match {
					/**
					 * ---------------------------------------------------------------------------------------------
					 * External Entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEE: ExternalEntity[_, _] =>
						// insert/update
						if (oldVMO.isDefined) {
							// entity is updated
							val oldVM = oldVMO.get
							val oldT = oldVM.oneToMany(column)
							val newT = newVM.oneToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEE, oldT, newT)

							UpdateExternalOneToManyCmd(
								foreignEE,
								ci.asInstanceOf[ColumnInfoTraversableOneToMany[ID, T, Any, Any]],
								newVM,
								added.toList,
								intersect.toList,
								removed.toList) :: Nil
						} else {
							val added = newVM.oneToMany(column)
							InsertOneToManyExternalCmd(
								foreignEE,
								ci.asInstanceOf[ColumnInfoTraversableOneToMany[ID, T, Any, Any]],
								newVM,
								added.toList) :: Nil
						}

					/**
					 * ---------------------------------------------------------------------------------------------
					 * Normal Entity
					 * ---------------------------------------------------------------------------------------------
					 */
					case foreignEntity =>
						val foreignTpe = foreignEntity.tpe
						val newT = newVM.oneToMany(column)

						if (oldVMO.isDefined) {
							// updating entity
							val oldVM = oldVMO.get
							val oldT = oldVM.oneToMany(column)
							val newT = newVM.oneToMany(column)
							// we'll find what was added, intersect (stayed in the collection but might have been updated)
							// and removed from the collection
							val (added, intersect, removed) = TraversableSeparation.separate(foreignEntity, oldT, newT)

							val addedCmds = added.toList.map {
								fo =>
									val foreignVM = vmFor(foreignTpe, fo)
									EntityRelatedCmd(foreignVM.identity, column, foreignVM, None, tpe, newVM, oldVMO, true) :: insertOrUpdate(foreignTpe, fo, updateConfig)
							}.flatten
							val removedCms = removed.toList.map {
								case fo: DeclaredIds[_] =>
									DeleteCmd(
										foreignTpe,
										fo.mapperDaoValuesMap
									)
							}

							val intersectCmds = intersect.toList.map {
								case (oldO, newO) =>
									val oVM = oldO match {
										case p: Persisted => p.mapperDaoValuesMap
										case _ => throw new IllegalStateException("unexpected object, please file a bug with code One-To-Many:NON_PERSISTED")
									}
									val nVM = vmFor(foreignTpe, newO)
									EntityRelatedCmd(nVM.identity, column, nVM, Some(oVM), tpe, newVM, oldVMO, true) :: update(foreignTpe, oVM, nVM, false, updateConfig)
							}.flatten
							addedCmds ::: removedCms ::: intersectCmds
						} else {
							// entity is new
							newT.map {
								case o =>
									// we need to insert the foreign entity and link to entity
									val foreignVM = vmFor(foreignTpe, o)

									(
										DependsCmd(foreignVM.identity, newVM.identity)
											::
											EntityRelatedCmd(foreignVM.identity, column, foreignVM, None, tpe, newVM, oldVMO, true)
											::
											insert(foreignTpe, foreignVM, false, updateConfig)
										)
							}.flatten
						}
				}

			/**
			 * ---------------------------------------------------------------------------------------------
			 * One-To-One
			 * ---------------------------------------------------------------------------------------------
			 */
			case ColumnInfoOneToOne(column, columnToValue) =>
				val fo = newVM.oneToOne(column)
				column.foreign.entity match {
					case foreignEntity =>
						val foreignTpe = foreignEntity.tpe
						val oldFoVMO = oldVMOf(oldVMO.map(_.oneToOne(column)))
						if (fo == null) {
							EntityRelatedCmd(0, column, newVM, oldVMO, foreignTpe, null, oldFoVMO, false) :: Nil
						} else {
							// insert new
							val foreignVM = vmFor(foreignTpe, fo)

							(
								EntityRelatedCmd(foreignVM.identity, column, newVM, oldVMO, foreignTpe, foreignVM, oldFoVMO, false)
									:: (fo match {
									case p: DeclaredIds[_] =>
										doUpdate(foreignTpe.asInstanceOf[Type[Any, Any]], p.asInstanceOf[Any with DeclaredIds[Any]], updateConfig)
									case _ =>
										// we need to insert the foreign entity and link to entity
										insert(foreignTpe, foreignVM, false, updateConfig)
								})
								)
						}
				}

			/**
			 * ---------------------------------------------------------------------------------------------
			 * One-To-One-Reverse
			 * ---------------------------------------------------------------------------------------------
			 */
			case ColumnInfoOneToOneReverse(column, columnToValue, _) =>
				val fo = newVM.oneToOneReverse(column)
				column.foreign.entity match {
					case foreignEntity =>
						val foreignTpe = foreignEntity.tpe
						if (fo == null) {
							val oldFVM = oldVMO.map {
								oldVM =>
									oldVM.oneToOneReverse(column) match {
										case oldFo: DeclaredIds[_] =>
											oldFo.mapperDaoValuesMap
									}
							}
							if (oldFVM.isDefined && oldFVM.get != null) {
								EntityRelatedCmd(0, column, null, oldFVM, tpe, newVM, oldVMO, true) :: DeleteCmd(foreignTpe, oldFVM.get) :: Nil
							} else {
								//								EntityRelatedCmd(newVM.identity, column, newVM, oldVMO, foreignTpe, null, oldFVM, true) :: Nil
								Nil
							}
						} else {
							val oldFo = oldVMO.map(_.oneToOneReverse(column))
							// insert new
							val foreignVM = vmFor(foreignTpe, fo)
							//EntityRelatedCmd(foreignVM.identity, column, newVM, oldVMO, foreignTpe, foreignVM, oldFoVMO, false)

							(
								EntityRelatedCmd(foreignVM.identity, column, foreignVM, None, tpe, newVM, oldVMO, true)
									:: (oldFo match {
									case Some(p: DeclaredIds[_]) =>
										update(foreignTpe, p.mapperDaoValuesMap, foreignVM, false, updateConfig)
									case None =>
										// we need to insert the foreign entity and link to entity
										insert(foreignTpe, foreignVM, false, updateConfig)
								})
								)
						}
				}

		}.flatten
	}

	private def oldVMOf(o: Option[Any]): Option[ValuesMap] = o match {
		case Some(p: Persisted) => Some(p.mapperDaoValuesMap)
		case Some(null) => o.asInstanceOf[Some[ValuesMap]]
		case None => None
	}

	private def insertOrUpdate[ID, T](tpe: Type[ID, T], o: T, updateConfig: UpdateConfig) = o match {
		case p: T with DeclaredIds[ID] => doUpdate(tpe, p, updateConfig)
		case _ => doInsert(tpe, o, updateConfig)
	}

	private def doInsert[ID, T](tpe: Type[ID, T], o: T, updateConfig: UpdateConfig) = {
		val newVM = vmFor(tpe, o)
		insert(tpe, newVM, false, updateConfig)
	}

	private def doUpdate[ID, T](tpe: Type[ID, T], p: T with DeclaredIds[ID], updateConfig: UpdateConfig) = {
		val newVM = vmFor(tpe, p)
		update(tpe, p.mapperDaoValuesMap, newVM, false, updateConfig)
	}

	def findVM(fo: Any) = vms(System.identityHashCode(fo))

	private val vms = scala.collection.mutable.HashMap.empty[Int, ValuesMap]

	private def vmFor[T](tpe: Type[_, T], o: T) = vms.getOrElseUpdate(System.identityHashCode(o), ValuesMap.fromType(typeManager, tpe, o))
}