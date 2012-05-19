package com.googlecode.mapperdao

case class ManyToMany[FPC, F](linkTable: LinkTable, foreign: TypeRef[FPC, F]) extends ColumnRelationshipBase(foreign) {
	def alias = foreign.alias

	override def columns: List[Column] = Nil
	override def toString = "ManyToMany(%s,%s)".format(foreign.entity.getClass.getSimpleName, columns.map(_.name).mkString(","))
}
