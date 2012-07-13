package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao._
import com.googlecode.mapperdao.jdbc.JdbcMap
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.jdbc.UpdateResult
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder

/**
 * all database drivers must implement this trait
 *
 * @author kostantinos.kougios
 *
 * 14 Jul 2011
 */
abstract class Driver {
	val jdbc: Jdbc
	val typeRegistry: TypeRegistry
	val typeManager: TypeManager

	/**
	 * =====================================================================================
	 * utility methods
	 * =====================================================================================
	 */
	val escapeNamesStrategy: EscapeNamesStrategy
	val sqlBuilder: SqlBuilder

	protected[mapperdao] def commaSeparatedListOfSimpleTypeColumns[T](separator: String, columns: Traversable[SimpleColumn], prefix: String = ""): String =
		columns.map(_.name).map(prefix + escapeNamesStrategy.escapeColumnNames(_)).mkString(separator)
	protected[mapperdao] def commaSeparatedListOfSimpleTypeColumns[T](prefix: String, separator: String, columns: List[SimpleColumn]): String =
		columns.map(_.name).map(escapeNamesStrategy.escapeColumnNames _).mkString(prefix, separator + prefix, "")

	protected[mapperdao] def generateColumnsEqualsValueString(l: List[SimpleColumn]): String = generateColumnsEqualsValueString(l, ",\n")

	protected[mapperdao] def generateColumnsEqualsValueString(l: List[SimpleColumn], separator: String): String =
		{
			val sb = new StringBuilder(20)
			var cnt = 0
			l.foreach { ci =>
				if (cnt > 0) sb.append(separator) else cnt += 1
				sb append escapeNamesStrategy.escapeColumnNames(ci.name) append "=?"
			}
			sb.toString
		}
	protected[mapperdao] def generateColumnsEqualsValueString(prefix: String, separator: String, l: List[SimpleColumn]): String =
		{
			val sb = new StringBuilder(20)
			var cnt = 0
			l.foreach { ci =>
				if (cnt > 0) sb.append(separator) else cnt += 1
				sb append prefix append escapeNamesStrategy.escapeColumnNames(ci.name) append "=?"
			}
			sb.toString
		}

	protected[mapperdao] def getAutoGenerated(ur: UpdateResultWithGeneratedKeys, column: SimpleColumn): Any =
		ur.keys.get(column.name).get

	/**
	 * =====================================================================================
	 * INSERT
	 * =====================================================================================
	 */

	/**
	 * default implementation of insert, should do for most subclasses
	 */
	def doInsert[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)]): UpdateResultWithGeneratedKeys =
		{
			val sql = insertSql(tpe, args)
			val a = args.map(_._2)

			val agColumns = tpe.table.autoGeneratedColumns.map(_.name).toArray
			if (agColumns.isEmpty) {
				val ur = jdbc.update(sql, a)
				new UpdateResultWithGeneratedKeys(ur.rowsAffected, Map())
			} else {
				jdbc.updateGetAutoGenerated(sql, agColumns, a)
			}
		}

	protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("Please implement")
	/**
	 * default impl of the insert statement generation
	 */
	protected def insertSql[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "insert into ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name)

			val sequenceColumns = tpe.table.simpleTypeSequenceColumns
			if (!args.isEmpty || !sequenceColumns.isEmpty) {
				sb append "("
				// append sequences
				// and normal columns
				if (!args.isEmpty || !sequenceColumns.isEmpty) sb append commaSeparatedListOfSimpleTypeColumns(",", sequenceColumns ::: args.map(_._1))
				sb append ")\n"
				sb append "values("
				// sequence values
				if (!sequenceColumns.isEmpty) {
					sb append sequenceColumns.map { sequenceSelectNextSql _ }.mkString(",")
					if (!args.isEmpty) sb append ","
				}
				// column values
				if (!args.isEmpty) sb append "?" append (",?" * (args.size - 1))
				sb append ")"
			}
			sb.toString
		}

	def doInsertManyToMany[PC, T, FPC, F](
		tpe: Type[PC, T],
		manyToMany: ManyToMany[FPC, F],
		left: List[Any],
		right: List[Any]): Unit =
		{
			val sql = insertManyToManySql(tpe, manyToMany)
			jdbc.update(sql, left ::: right)
		}

	protected def insertManyToManySql[PC, T, FPC, F](tpe: Type[PC, T], manyToMany: ManyToMany[FPC, F]): String =
		{
			val sb = new StringBuilder(100, "insert into ")
			val linkTable = manyToMany.linkTable
			sb append escapeNamesStrategy.escapeTableNames(linkTable.name) append "(" append commaSeparatedListOfSimpleTypeColumns(",", linkTable.left)
			sb append "," append commaSeparatedListOfSimpleTypeColumns(",", linkTable.right) append ")\n"
			sb append "values(?" append (",?" * (linkTable.left.size - 1 + linkTable.right.size)) append ")"
			sb.toString
		}
	/**
	 * =====================================================================================
	 * UPDATE
	 * =====================================================================================
	 */
	/**
	 * default implementation of update, should do for most subclasses
	 */
	def doUpdate[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = updateSql(tpe, args, pkArgs)
			jdbc.update(sql, args.map(_._2) ::: pkArgs.map(_._2))
		}
	/**
	 * default impl of the insert statement generation
	 */
	protected def updateSql[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "update ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name) append "\n"
			sb append "set " append generateColumnsEqualsValueString(args.map(_._1))
			sb append "\nwhere " append generateColumnsEqualsValueString(pkArgs.map(_._1), " and ")
			sb.toString
		}

	/**
	 * links one-to-many objects to their parent
	 */
	def doUpdateOneToManyRef[PC, T](tpe: Type[PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = updateOneToManyRefSql(tpe, foreignKeys, pkArgs)
			jdbc.update(sql, foreignKeys.map(_._2) ::: pkArgs.map(_._2))
		}

	protected def updateOneToManyRefSql[PC, T](tpe: Type[PC, T], foreignKeys: List[(SimpleColumn, Any)], pkArgs: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "update ")
			sb append escapeNamesStrategy.escapeTableNames(tpe.table.name) append "\n"
			sb append "set " append generateColumnsEqualsValueString(foreignKeys.map(_._1))
			sb append "\nwhere " append generateColumnsEqualsValueString(pkArgs.map(_._1))
			sb.toString
		}

	/**
	 * delete many-to-many rows from link table
	 */
	def doDeleteManyToManyRef[PC, T, PR, R](tpe: Type[PC, T], ftpe: Type[PR, R], manyToMany: ManyToMany[_, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]): UpdateResult =
		{
			val sql = deleteManyToManyRefSql(tpe, ftpe, manyToMany, leftKeyValues, rightKeyValues)
			jdbc.update(sql, leftKeyValues.map(_._2) ::: rightKeyValues.map(_._2))
		}
	protected def deleteManyToManyRefSql[PC, T, PR, R](tpe: Type[PC, T], ftpe: Type[PR, R], manyToMany: ManyToMany[_, _], leftKeyValues: List[(SimpleColumn, Any)], rightKeyValues: List[(SimpleColumn, Any)]): String =
		{
			val sb = new StringBuilder(100, "delete from ")
			sb append escapeNamesStrategy.escapeTableNames(manyToMany.linkTable.name) append "\nwhere "
			sb append generateColumnsEqualsValueString("", " and ", leftKeyValues.map(_._1) ::: rightKeyValues.map(_._1))
			sb.toString
		}

	def doDeleteAllManyToManyRef[PC, T](tpe: Type[PC, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]): UpdateResult = {
		val sql = deleteAllManyToManyRef(tpe, manyToMany, fkKeyValues)
		jdbc.update(sql, fkKeyValues)
	}
	protected def deleteAllManyToManyRef[PC, T](tpe: Type[PC, T], manyToMany: ManyToMany[_, _], fkKeyValues: List[Any]): String = {
		val sb = new StringBuilder(50, "delete from ")
		sb append escapeNamesStrategy.escapeTableNames(manyToMany.linkTable.name) append "\nwhere "
		sb append generateColumnsEqualsValueString("", " and ", manyToMany.linkTable.left)
		sb.toString
	}
	/**
	 * =====================================================================================
	 * SELECT
	 * =====================================================================================
	 */
	def selectColumns[PC, T](tpe: Type[PC, T]): List[SimpleColumn] =
		{
			val table = tpe.table
			table.simpleTypeColumns ::: table.manyToOneColumns.map(_.columns).flatten ::: table.oneToOneColumns.map(_.selfColumns).flatten
		}
	/**
	 * default impl of select
	 */
	def doSelect[PC, T](selectConfig: SelectConfig, tpe: Type[PC, T], where: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val result = selectSql(selectConfig, tpe, where).result

			// 1st step is to get the simple values
			// of this object from the database
			jdbc.queryForList(result.sql, result.values).map(j => typeManager.correctTypes(tpe.table, j))
		}

	protected def selectSql[PC, T](selectConfig: SelectConfig, tpe: Type[PC, T], where: List[(SimpleColumn, Any)]) =
		{
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null,
				(
					selectColumns(tpe) ::: tpe.table.unusedPrimaryKeyColumns.collect {
						case c: SimpleColumn => c
					}
				).map(_.name).distinct
			)
			sql.from(tpe.table.name, null, applyHints(selectConfig.hints))
			sql.whereAll(null, where.map {
				case (c, v) =>
					(c.name, v)
			}, "=")
			sql
		}

	private def applyHints(hints: SelectHints) = {
		val h = hints.afterTableName
		if (!h.isEmpty) {
			" " + h.map { _.hint }.mkString(" ") + " "
		} else ""
	}

	def doSelectManyToMany[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[DatabaseValues] =
		{
			val r = selectManyToManySql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values).map(j => typeManager.correctTypes(ftpe.table, j))
		}

	protected def selectManyToManySql[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			val fColumns = selectColumns(ftpe)
			sql.columns("f", fColumns.map { _.name })
			sql.from(ftpe.table.name, "f", applyHints(selectConfig.hints))
			val j = sql.innerJoin(linkTable.name, "l", applyHints(selectConfig.hints))
			ftable.primaryKeys.zip(linkTable.right).foreach {
				case (left, right) =>
					j.and("f", left.name, "=", "l", right.name)
			}
			val wcs = leftKeyValues.map {
				case (c, v) => (c.name, v)
			}
			sql.whereAll("l", wcs, "=")
			sql
		}

	def doSelectManyToManyCustomLoader[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[JdbcMap] =
		{
			val r = selectManyToManyCustomLoaderSql(selectConfig, tpe, ftpe, manyToMany, leftKeyValues).result
			jdbc.queryForList(r.sql, r.values)
		}

	protected def selectManyToManyCustomLoaderSql[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val ftable = ftpe.table
			val linkTable = manyToMany.linkTable
			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right.map(_.name))
			sql.from(linkTable.name, null, applyHints(selectConfig.hints))
			sql.whereAll(null, leftKeyValues.map {
				case (c, v) => (c.name, v)
			}, "=")
			sql

		}
	/**
	 * selects all id's of external entities and returns them in a List[List[Any]]
	 */
	def doSelectManyToManyForExternalEntity[PC, T, FPC, F](selectConfig: SelectConfig, tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]): List[List[Any]] =
		{
			val r = selectManyToManySqlForExternalEntity(tpe, ftpe, manyToMany, leftKeyValues).result
			val l = jdbc.queryForList(r.sql, r.values)

			val linkTable = manyToMany.linkTable
			val columns = linkTable.right.map(_.name)
			l.map { j =>
				columns.map(c => j(c))
			}
		}

	protected def selectManyToManySqlForExternalEntity[PC, T, FPC, F](tpe: Type[PC, T], ftpe: Type[FPC, F], manyToMany: ManyToMany[FPC, F], leftKeyValues: List[(SimpleColumn, Any)]) =
		{
			val linkTable = manyToMany.linkTable

			val sql = new sqlBuilder.SqlSelectBuilder
			sql.columns(null, linkTable.right.map(_.name))
			sql.from(linkTable.name)
			sql.whereAll(null, leftKeyValues.map {
				case (c, v) => (c.name, v)
			}, "=")
			sql
		}
	/**
	 * =====================================================================================
	 * DELETE
	 * =====================================================================================
	 */
	def doDelete[PC, T](tpe: Type[PC, T], whereColumnValues: List[(SimpleColumn, Any)]): Unit =
		{
			val s = deleteSql(tpe, whereColumnValues).result
			jdbc.update(s.sql, s.values)
		}

	protected def deleteSql[PC, T](tpe: Type[PC, T], whereColumnValues: List[(SimpleColumn, Any)]) =
		{
			val s = new sqlBuilder.DeleteBuilder
			s.from(sqlBuilder.Table(tpe.table.name, null, null))
			s.where(sqlBuilder.whereAllColumns(null, whereColumnValues, "="))
			s
		}

	def doDeleteOneToOneReverse[PC, T, FPC, FT](tpe: Type[PC, T], ftpe: Type[FPC, FT], oneToOneReverse: OneToOneReverse[FPC, FT], keyValues: List[Any]): Unit =
		{
			val sql = deleteOneToOneReverseSql(tpe, ftpe, oneToOneReverse)
			jdbc.update(sql, keyValues)
		}

	def deleteOneToOneReverseSql[PC, T, FPC, FT](tpe: Type[PC, T], ftpe: Type[FPC, FT], oneToOneReverse: OneToOneReverse[FPC, FT]): String =
		{
			val sb = new StringBuilder(100, "delete from ")
			sb append escapeNamesStrategy.escapeTableNames(ftpe.table.name) append " where " append generateColumnsEqualsValueString(oneToOneReverse.foreignColumns, " and ")

			sb.toString
		}
	/**
	 * =====================================================================================
	 * QUERIES
	 * =====================================================================================
	 */

	// select ... from 
	def startQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[PC, T], columns: List[SimpleColumn]) =
		{
			val entity = qe.entity
			val tpe = entity.tpe
			queryAfterSelect(q, queryConfig, aliases, qe, columns)
			val alias = aliases(entity)

			q.columns(alias, columns.map(_.name))
			q.from(tpe.table.name, alias, null)
		}

	def queryAfterSelect[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, aliases: QueryDao.Aliases, qe: Query.Builder[PC, T], columns: List[SimpleColumn]): Unit = {}

	def shouldCreateOrderByClause(queryConfig: QueryConfig): Boolean = true

	// called at the start of each query sql generation, sql is empty at this point
	def beforeStartOfQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T], columns: List[SimpleColumn]): sqlBuilder.SqlSelectBuilder = q
	// called at the end of each query sql generation
	def endOfQuery[PC, T](q: sqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T]): sqlBuilder.SqlSelectBuilder = q

	/**
	 * =====================================================================================
	 * generic queries
	 * =====================================================================================
	 */
	def queryForList[PC, T](queryConfig: QueryConfig, tpe: Type[PC, T], sql: String, args: List[Any]): List[DatabaseValues] =
		jdbc.queryForList(sql, args).map { j => typeManager.correctTypes(tpe.table, j) }
	def queryForLong(queryConfig: QueryConfig, sql: String, args: List[Any]): Long = jdbc.queryForLong(sql, args)
	/**
	 * =====================================================================================
	 * standard methods
	 * =====================================================================================
	 */
	override def toString = "Driver(%s,%s)".format(jdbc, typeRegistry)
}