package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao.jdbc.Jdbc
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.ColumnBase
import com.googlecode.mapperdao.Type
import com.googlecode.mapperdao.PK
import com.googlecode.mapperdao.QueryConfig
import com.googlecode.mapperdao.Query
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.TypeManager
import com.googlecode.mapperdao.SimpleColumn

/**
 * @author kostantinos.kougios
 *
 * 14 Jul 2011
 */
class Derby(override val jdbc: Jdbc, val typeRegistry: TypeRegistry, val typeManager: TypeManager) extends Driver {

	private val invalidColumnNames = Set("end", "select", "where", "group", "year", "no")
	private val invalidTableNames = Set("end", "select", "where", "group", "user", "User")

	override def escapeColumnNames(name: String) = if (invalidColumnNames.contains(name)) '"' + name + '"'; else name
	override def escapeTableNames(name: String): String = if (invalidTableNames.contains(name)) '"' + name + '"'; else name

	protected[mapperdao] override def getAutoGenerated(ur: UpdateResultWithGeneratedKeys, column: SimpleColumn): Any =
		ur.keys.get("1").get

	override protected def sequenceSelectNextSql(sequenceColumn: ColumnBase): String = sequenceColumn match {
		case PK(columnName, true, sequence) => "NEXT VALUE FOR %s".format(sequence.get)
	}

	override def endOfQuery[PC, T](queryConfig: QueryConfig, qe: Query.Builder[PC, T], sql: StringBuilder): Unit =
		{
			queryConfig.offset.foreach(sql append "\noffset " append _ append " rows")
			queryConfig.limit.foreach(sql append "\nfetch next " append _ append " rows only")
		}

	override def toString = "Derby"
}