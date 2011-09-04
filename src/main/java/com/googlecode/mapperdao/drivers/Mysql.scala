package com.googlecode.mapperdao.drivers
import com.googlecode.mapperdao.TypeRegistry
import com.googlecode.mapperdao.ColumnBase
import com.googlecode.mapperdao.jdbc.UpdateResultWithGeneratedKeys
import com.googlecode.mapperdao.jdbc.Jdbc

/**
 * @author kostantinos.kougios
 *
 * 2 Sep 2011
 */
class Mysql(override val jdbc: Jdbc, override val typeRegistry: TypeRegistry) extends Driver {

	protected[mapperdao] override def getAutoGenerated(ur: UpdateResultWithGeneratedKeys, column: ColumnBase): Any = ur.keys.get("GENERATED_KEY").get
}