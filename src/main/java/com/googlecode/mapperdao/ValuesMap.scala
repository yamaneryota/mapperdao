package com.googlecode.mapperdao
import scala.collection.mutable.Buffer
import java.util.Calendar
import org.joda.time.DateTime
import com.googlecode.mapperdao.utils.ISet

/**
 * @author kostantinos.kougios
 *
 * 16 Jul 2011
 */
class ValuesMap(typeManager: TypeManager, protected[mapperdao] var m: scala.collection.mutable.Map[String, Any]) {
	protected[mapperdao] def apply[T](column: String): T = typeManager.deepClone(m.getOrElse(column, null).asInstanceOf[T])

	private def update[T, V](column: ColumnInfo[T, _], v: V): Unit =
		{
			val key = column.column.columnName
			m(key) = v
		}

	def apply[T, V](column: ColumnInfo[T, V]): V =
		{
			val key = column.column.columnName
			apply[V](key)
		}
	def apply[T, F](column: ColumnInfoOneToOne[T, F]): F =
		{
			val key = column.column.alias
			apply[F](key)
		}
	def apply[T, F](column: ColumnInfoOneToOneReverse[T, F]): F =
		{
			val key = column.column.alias
			apply[F](key)
		}

	def apply[T, V](column: ColumnInfoTraversableOneToMany[T, V]): Traversable[V] =
		{
			val key = column.column.alias
			apply[Traversable[V]](key)
		}
	def apply[T, V](column: ColumnInfoTraversableManyToMany[T, V]): Traversable[V] =
		{
			val key = column.column.alias
			apply[Traversable[V]](key)
		}
	def apply[T, F](column: ColumnInfoManyToOne[T, F]) =
		{
			val key = column.column.alias
			apply[F](key)
		}

	def float[T, V](column: ColumnInfo[T, V]): Float =
		{
			val v = apply(column)
			v match {
				case f: Float => f
				case b: java.math.BigDecimal =>
					val v = b.floatValue
					update(column, v)
					v
				case b: java.math.BigInteger =>
					val v = b.floatValue
					update(column, v)
					v
			}
		}

	def double[T, V](column: ColumnInfo[T, V]): Double =
		{
			val v = apply(column)
			v match {
				case d: Double => d
				case b: java.math.BigDecimal =>
					val v = b.doubleValue
					update(column, v)
					v
				case b: java.math.BigInteger =>
					val v = b.doubleValue
					update(column, v)
					v
			}
		}

	def int[T, V](column: ColumnInfo[T, V]): Int =
		{
			val v = apply(column)
			v match {
				case i: Int => i
				case l: Long => l.toInt
				case s: Short => s.toInt
				case b: BigInt => b.toInt
				case b: java.math.BigInteger => b.intValue
				case null => 0
			}
		}

	def long[T, V](column: ColumnInfo[T, V]): Long =
		{
			val v = apply(column)
			v match {
				case l: Long => l
				case i: Int => i.toLong
				case s: Short => s.toLong
				case b: BigInt => b.toLong
				case b: java.math.BigInteger => b.longValue
				case null => 0
			}
		}

	def bigDecimal[T, V](column: ColumnInfo[T, V]): BigDecimal =
		{
			val v = apply(column)
			v match {
				case bd: BigDecimal => bd
				case d: Double =>
					val v = BigDecimal(d)
					update(column, v)
					v
				case b: java.math.BigDecimal =>
					val v = BigDecimal(b)
					update(column, v)
					v
			}
		}

	def bigInt[T, V](column: ColumnInfo[T, V]): BigInt =
		{
			val v = apply(column)
			v match {
				case i: BigInt => i
				case i: Int =>
					val v = BigInt(i)
					update(column, v)
					v
				case l: Long =>
					val v = BigInt(l)
					update(column, v)
					v
			}
		}

	def boolean[T, V](column: ColumnInfo[T, V]): Boolean =
		{
			val v = apply(column)
			v match {
				case b: Boolean => b
				case i: Int =>
					val v = i == 1
					update(column, v)
					v
			}
		}

	def iset[T, V](column: ColumnInfoTraversableOneToMany[T, V]): ISet[V] =
		{
			val key = column.column.alias
			val t = apply[Traversable[V]](key)
			new ISet(this, column.column.columnName)
		}
	def iset[T, V](column: ColumnInfoTraversableManyToMany[T, V]): ISet[V] =
		{
			val key = column.column.alias
			val t = apply[Traversable[V]](key)
			new ISet(this, key)
		}

	/**
	 * the following methods do a conversion
	 */
	//	def traversable[T](column: String): Traversable[T] = apply(column).asInstanceOf[Traversable[T]]
	//	def list[T](column: String): List[T] = apply(column).asInstanceOf[Traversable[T]].toList
	protected[mapperdao] def set[T](column: String): Set[T] = apply(column).asInstanceOf[Traversable[T]].toSet
	//	def iset[T](column: String): Set[T] = new ISet(this, column)
	protected[mapperdao] def seq[T](column: String): Seq[T] = apply(column).asInstanceOf[Traversable[T]].toSeq
	//	def indexedSeq[T](column: String): Seq[T] = apply(column).asInstanceOf[Traversable[T]].toIndexedSeq
	//	def buffer[T](column: String): Buffer[T] = apply(column).asInstanceOf[Traversable[T]].toBuffer

	override def toString = m.toString

	protected[mapperdao] def toMutableMap: scala.collection.mutable.Map[String, Any] = new scala.collection.mutable.HashMap() ++ m

	protected[mapperdao] def toListOfColumnAndValueTuple(columns: List[ColumnBase]) = columns.map(c => (c, m(c.alias)))
	protected[mapperdao] def toListOfColumnValue(columns: List[ColumnBase]) = columns.map(c => m(c.alias))
}

object ValuesMap {
	protected[mapperdao] def fromEntity[PC, T](typeManager: TypeManager, tpe: Type[PC, T], o: T): ValuesMap =
		{
			val m = tpe.table.toColumnAliasAndValueMap(tpe.table.columnsWithoutAutoGenerated, o).map(e => (e._1, typeManager.deepClone(e._2)))
			val nm = new scala.collection.mutable.HashMap[String, Any]
			nm ++= m
			new ValuesMap(typeManager, nm)
		}
	protected[mapperdao] def fromMutableMap(typeManager: TypeManager, m: scala.collection.mutable.Map[String, Any]): ValuesMap =
		{
			val nm = new scala.collection.mutable.HashMap[String, Any]
			nm ++= m
			new ValuesMap(typeManager, nm)
		}
}