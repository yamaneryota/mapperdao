package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.JdbcMap

/**
 * manages types
 *
 * @author kostantinos.kougios
 *
 * 30 Jul 2011
 */
trait TypeManager {

	/**
	 * creates a deep clone of the object
	 */
	def deepClone[T](o: T): T

	def convert(o: Any): Any

	def reverseConvert(o: Any): Any

	/**
	 * converts o to tpe if possible
	 */
	def toActualType(tpe: Class[_], o: Any): Any

	def correctTypes[PC, T](table: Table[PC, T], j: JdbcMap): DatabaseValues
}