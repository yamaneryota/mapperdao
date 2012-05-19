package com.googlecode.mapperdao

case class Column(val name: String) extends SimpleColumn {
	def alias = name
	def isAutoGenerated = false
	def isSequence = false
}
