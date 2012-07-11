package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.ColumnBase
import com.googlecode.mapperdao.SimpleColumn
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.QueryConfig
import com.googlecode.mapperdao.Query
import com.googlecode.mapperdao.TypeManager
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder

/**
 * @author kostantinos.kougios
 *
 * 2 Sep 2011
 */
class Mysql(override val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver {

	val escapeNamesStrategy = new EscapeNamesStrategy {
		override def escapeColumnNames(name: String) = name
		override def escapeTableNames(name: String) = name
	}

	override protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = throw new IllegalStateException("MySql doesn't support sequences")

	override protected def insertSql[PC, T](tpe: Type[PC, T], args: List[(SimpleColumn, Any)]): String =
		{
			val sql = super.insertSql(tpe, args)
			if (args.isEmpty) {
				sql + "\nvalues()"
			} else sql
		}

	protected[mapperdao] override def getAutoGenerated(ur: UpdateResultWithGeneratedKeys, column: SimpleColumn): Any = ur.keys.get("GENERATED_KEY").get

	override def endOfQuery[PC, T](q: SqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T]) = {
		if (queryConfig.offset.isDefined || queryConfig.limit.isDefined) {
			val offset = queryConfig.offset.getOrElse(0)
			val limit = queryConfig.limit.getOrElse(Long.MaxValue)
			q.appendSql("LIMIT " + offset + "," + limit)
		}
		q
	}

	override def toString = "MySql"
}