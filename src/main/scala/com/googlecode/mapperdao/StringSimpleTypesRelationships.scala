package com.googlecode.mapperdao

/**
 * string simple type
 */
case class StringValue(val value: String) extends SimpleTypeValue[String, StringValue] {
	def compareTo(o: StringValue): Int = value.compareTo(o.value)
}

protected class StringEntityOneToMany(table: String, fkColumn: String, soleColumn: String)
		extends Entity[Nothing, NoId, StringValue](table, classOf[StringValue]) {
	val value = column(soleColumn) to (_.value)

	declarePrimaryKey(value)
	def constructor(implicit m: ValuesMap) = new StringValue(value) with NoId
}

abstract class StringEntityManyToManyBase[ID, PC <: DeclaredIds[ID]](table: String, soleColumn: String)
		extends Entity[ID, PC, StringValue](table, classOf[StringValue]) {
	val value = column(soleColumn) to (_.value)
}
class StringEntityManyToManyAutoGenerated(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None)
		extends StringEntityManyToManyBase[Int, SurrogateIntId](table, soleColumn) {
	val id = key(pkColumn) sequence (sequence) autogenerated (_.id)
	def constructor(implicit m: ValuesMap) = new StringValue(value) with Persisted with SurrogateIntId {
		val id: Int = StringEntityManyToManyAutoGenerated.this.id
	}
}

/**
 * Factory methods for entities that hold just a string value
 */
object StringEntity {
	/**
	 * maps a string value as an entity for a one-to-many relationship.
	 *
	 * @param	table		the table name for the string entity
	 * @param	fkColumn	the column that is FK to the parent entity
	 * @param	soleColumn	the sole column name that holds the string value
	 */
	def oneToMany(table: String, fkColumn: String, soleColumn: String) = new StringEntityOneToMany(table, fkColumn, soleColumn)
	def manyToManyAutoGeneratedPK(table: String, pkColumn: String, soleColumn: String, sequence: Option[String] = None) = new StringEntityManyToManyAutoGenerated(table, pkColumn, soleColumn, sequence)
}