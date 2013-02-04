package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.state.persistcmds._
import com.googlecode.mapperdao.drivers.Driver
import com.googlecode.mapperdao.state.persisted._
import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.state.persistcmds.InsertCmd
import state.prioritise.Prioritized

/**
 * converts commands to database operations, executes
 * them and returns the resulting persisted nodes.
 *
 * @author kostantinos.kougios
 *
 *         22 Nov 2012
 */
class CmdToDatabase(
	updateConfig: UpdateConfig,
	protected val driver: Driver,
	typeManager: TypeManager,
	prioritized: Prioritized
	) {

	private val jdbc = driver.jdbc

	// keep track of which entities were already persisted in order to
	// know if a depended entity can be persisted
	private val persistedIdentities = scala.collection.mutable.HashSet[Int]()

	val dependentMap = prioritized.dependent.groupBy(_.identity).map {
		case (identity, l) =>
			(identity, l.map {
				_.dependsOnIdentity
			}.toSet)
	}

	// true if all needed related entities are already persisted.
	private def allDependenciesAlreadyPersisted(identity: Int) = dependentMap.get(identity) match {
		case None => true
		case Some(set) => set.forall(persistedIdentities(_))
	}

	private case class Node(
		sql: driver.sqlBuilder.Result,
		cmd: PersistCmd
		)

	def execute: List[PersistedNode[_, _]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		val cmdList = (prioritized.high ::: List(prioritized.low))

		/**
		 * cmdList contains a list of prioritized PersistCmd, according to their
		 * relevant entity priority. Some times related entities still are
		 * scheduled to be persisted before the entity that references them.
		 * i.e. a one-to-many Person(name,Set[Person])
		 *
		 * We need to make sure that all entities are persisted in the
		 * correct order.
		 */
		def persist(cmdList: List[List[PersistCmd]], depth: Int) {
			if (depth > 100) throw new IllegalStateException("after 100 iterations, there are still unpersisted entities. Maybe a mapperdao bug. Entities remaining : " + cmdList)
			val remaining = cmdList.map {
				cmds =>
					val (toProcess, remaining) = findToProcess(cmds)
					val nodes = toNodes(toProcess)
					toDb(nodes)
					remaining
			}.filterNot(_.isEmpty)
			if (!remaining.isEmpty) persist(remaining, depth + 1)
		}

		persist(cmdList, 0)

		cmdList.map {
			cmds =>
			// now the batches were executed and we got a tree with
			// the commands and the autogenerated keys.
				toPersistedNodes(cmds)
		}.flatten
	}

	private def findToProcess(cmds: List[PersistCmd]) = cmds.partition {
		case c: CmdWithNewVM =>
			allDependenciesAlreadyPersisted(c.newVM.identity)
		case _ => true
	}

	private def toDb(nodes: List[Node]) {
		// group the sql's and batch-execute them
		nodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val cmd = nodes.head.cmd
				val args = nodes.map {
					case Node(sql, _) =>
						sql.values.toArray
				}.toArray

				cmd match {
					case cmdWe: CmdWithType[_, _] =>
						val tpe = cmdWe.tpe
						val table = tpe.table
						val autoGeneratedColumnNames = cmd match {
							case InsertCmd(_, _, _, _) =>
								table.autoGeneratedColumnNamesArray
							case _ => Array[String]()
						}
						val bo = BatchOptions(driver.batchStrategy(autoGeneratedColumnNames.length > 0), autoGeneratedColumnNames)

						// do the batch update
						val br = jdbc.batchUpdate(bo, sql, args)

						// now extract the keys and set them into the nodes
						if (br.keys != null) {
							val keys: Array[List[(SimpleColumn, Any)]] = br.keys.map {
								m: java.util.Map[String, Object] =>
									table.autoGeneratedColumns.map {
										column =>
											(
												column,
												table.pcColumnToColumnInfoMap(column) match {
													case ci: ColumnInfo[_, _] =>
														val value = driver.getAutoGenerated(m, column)
														typeManager.toActualType(ci.dataType, value)
												}
												)
									}
							}
							// note down the generated keys
							(nodes zip keys) foreach {
								case (node, keys) =>
									node.cmd match {
										case InsertCmd(_, newVM, _, _) =>
											newVM.addAutogeneratedKeys(keys)
									}
							}
						}
					case InsertManyToManyCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case DeleteManyToManyCmd(entity, foreignEntity, manyToMany, entityVM, foreignEntityVM) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case UpdateExternalManyToManyCmd(_, _, _, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
				}
		}
	}

	private def toPersistedNodes(nodes: List[PersistCmd]) = nodes.collect {
		case InsertCmd(tpe, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, None, newVM, mainEntity) :: Nil
		case UpdateCmd(tpe, oldVM, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, Some(oldVM), newVM, mainEntity) :: Nil
		case UpdateExternalManyToManyCmd(tpe, newVM, foreignEntity, manyToMany, added, intersect, removed) =>
			val add = added.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}.toList
			val in = intersect.map {
				case (oldO, newO) =>
					ExternalEntityPersistedNode(foreignEntity, newO)
			}.toList
			val rem = removed.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}.toList
			add ::: in ::: rem ::: Nil
		case UpdateExternalManyToOneCmd(foreignEntity, ci, newVM, fo) =>
			ExternalEntityPersistedNode(foreignEntity, fo) :: Nil
		case InsertOneToManyExternalCmd(foreignEntity, oneToMany, entityVM, added) =>
			val ue = UpdateExternalOneToMany(updateConfig, entityVM, added, Nil, Nil)
			foreignEntity.oneToManyOnUpdateMap(oneToMany)(ue)
			added.map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			}
		case UpdateExternalOneToManyCmd(foreignEntity, oneToMany, entityVM, added, intersected, removed) =>
			val ue = UpdateExternalOneToMany(updateConfig, entityVM, added, intersected, removed)
			foreignEntity.oneToManyOnUpdateMap(oneToMany)(ue)
			(added ++ removed).map {
				fo =>
					ExternalEntityPersistedNode(foreignEntity, fo)
			} ++ intersected.map {
				case (o, n) =>
					ExternalEntityPersistedNode(foreignEntity, n)
			}
	}.flatten

	private def toNodes(cmds: List[PersistCmd]) =
		cmds.map {
			cmd =>
				toSql(cmd).map {
					sql =>
						Node(
							sql,
							cmd
						)
				}
		}.flatten

	protected[jdbc] def toSql(cmd: PersistCmd): List[driver.sqlBuilder.Result] =
		cmd match {
			case ic@InsertCmd(tpe, newVM, columns, _) =>
				persistedIdentities += ic.identity
				val related = prioritized.relatedColumns(newVM, false).distinct.filterNot(t => ic.columnNames.contains(t._1.name))
				driver.insertSql(tpe, columns ::: related).result :: Nil

			case uc@UpdateCmd(tpe, oldVM, newVM, columns, _) =>
				persistedIdentities += uc.identity
				val oldRelated = prioritized.relatedColumns(oldVM, true)
				val newRelated = prioritized.relatedColumns(newVM, false)
				val set = columns ::: newRelated.filterNot(n => oldRelated.contains(n))
				if (set.isEmpty)
					Nil
				else {
					val pks = oldVM.toListOfPrimaryKeyAndValueTuple(tpe)
					val relKeys = prioritized.relatedKeys(newVM)
					driver.updateSql(tpe, set, pks ::: relKeys).result :: Nil
				}

			case InsertManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				driver.insertManyToManySql(manyToMany, left, right).result :: Nil

			case DeleteManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
				val left = entityVM.toListOfPrimaryKeys(tpe)
				val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
				driver.deleteManyToManySql(manyToMany, left, right).result :: Nil

			case dc@DeleteCmd(tpe, vm) =>
				persistedIdentities += dc.identity
				val args = vm.toListOfPrimaryKeyAndValueTuple(tpe)
				driver.deleteSql(tpe, args).result :: Nil

			case UpdateExternalManyToOneCmd(foreignEE, ci, newVM, fo) =>
				val ie = UpdateExternalManyToOne(updateConfig, fo)
				foreignEE.manyToOneOnUpdateMap(ci)(ie)
				Nil
			case UpdateExternalManyToManyCmd(tpe, newVM, foreignEntity, manyToMany, added, intersection, removed) =>
				val fTable = foreignEntity.tpe.table

				// removed
				val de = DeleteExternalManyToMany(updateConfig.deleteConfig, removed)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(de)

				val rSqls = removed.map {
					fo =>
						val left = newVM.toListOfPrimaryKeys(tpe)
						val right = fTable.toListOfPrimaryKeyValues(fo)
						driver.deleteManyToManySql(manyToMany.column, left, right).result
				}.toList

				// updated
				val ue = UpdateExternalManyToMany(updateConfig, intersection)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(ue)

				// added
				val ie = InsertExternalManyToMany(updateConfig, added)
				foreignEntity.manyToManyOnUpdateMap(manyToMany)(ie)

				val aSqls = added.map {
					fo =>
						val left = newVM.toListOfPrimaryKeys(tpe)
						val right = fTable.toListOfPrimaryKeyValues(fo)
						driver.insertManyToManySql(manyToMany.column, left, right).result
				}.toList
				(rSqls ::: aSqls).toList
			case InsertOneToManyExternalCmd(foreignEntity, oneToMany, entityVM, added) =>
				Nil
			case UpdateExternalOneToManyCmd(foreignEntity, oneToMany, entityVM, added, intersected, removed) =>
				Nil
		}
}