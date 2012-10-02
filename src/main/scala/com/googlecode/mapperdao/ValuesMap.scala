package com.googlecode.mapperdao

import java.util.Calendar
import java.util.Date
import java.util.Locale

import org.joda.time.DateTime

import com.googlecode.mapperdao.utils.Equality
import scala.collection.JavaConverters._
import utils.MemoryEfficientMap
import utils.SynchronizedMemoryEfficientMap

/**
 * provides values that originate from the database. Those values are used by
 * Entity.constructor(implicit m:ValuesMap) to construct entities.
 *
 * Because ValuesMap is implicit, it can convert columns to their actual values
 *
 * @author kostantinos.kougios
 *
 * 16 Jul 2011
 */
class ValuesMap private (mOrig: scala.collection.Map[String, Any])
		extends MemoryEfficientMap[String, Any]
		with SynchronizedMemoryEfficientMap[String, Any] {

	initializeMEM(mOrig)

	override def transformKey(k: String) = k.toLowerCase

	def contains(c: ColumnBase) = containsMEM(c.alias)

	def columnValue[T](ci: ColumnInfoRelationshipBase[_, _, _, _, _]): T = columnValue(ci.column.alias)

	/**
	 * returns true if the relationship is not yet loaded
	 */
	def isLoaded(ci: ColumnInfoRelationshipBase[_, _, _, _, _]): Boolean = columnValue[Any](ci) match {
		case _: (() => Any) => false
		case _ => true
	}

	def columnValue[T](column: ColumnBase): T = columnValue(column.alias)

	def columnValue[T](column: String): T = {
		val v = getMEM(column)
		v.asInstanceOf[T]
	}

	protected[mapperdao] def valueOf[T](ci: ColumnInfoBase[_, _]): T = valueOf(ci.column.alias)

	protected[mapperdao] def valueOf[T](column: String): T = {
		// to avoid lazy loading twice in 2 separate threads, and avoid corrupting the map, we need to sync
		val v = synchronized {
			getMEMOrElse(column, null) match {
				case null => null
				case f: (() => Any) =>
					val v = f()
					putMEM(column, v)
					v
				case v => v
			}
		}
		ValuesMap.deepClone(v).asInstanceOf[T]
	}

	private[mapperdao] def update[T, V](column: ColumnInfoBase[T, _], v: V): Unit =
		{
			val key = column.column.alias
			putMEM(key, v)
		}

	private[mapperdao] def update[T, V](column: ColumnInfoRelationshipBase[T, _, _, _, _], v: V): Unit =
		{
			val key = column.column.alias
			putMEM(key, v)
		}

	def raw[T, V](column: ColumnInfo[T, V]): Option[Any] = {
		val key = column.column.name
		getMEMOption(key)
	}

	def apply[T, V](column: ColumnInfo[T, V]): V =
		{
			val key = column.column.name
			val v = valueOf[V](key)
			v
		}

	def isNull[T, V](column: ColumnInfo[T, V]): Boolean =
		valueOf[V](column.column.name) == null

	def apply[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoOneToOne[T, FID, FPC, F]): F =
		{
			val key = column.column.alias
			valueOf[F](key)
		}

	def apply[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoOneToOneReverse[T, FID, FPC, F]): F =
		{
			val key = column.column.alias
			valueOf[F](key)
		}

	def apply[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableOneToMany[T, FID, FPC, F]): Traversable[F] =
		{
			val key = column.column.alias
			valueOf[Traversable[F]](key)
		}

	def apply[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableManyToMany[T, FID, FPC, F]): Traversable[F] =
		{
			val key = column.column.alias
			valueOf[Traversable[F]](key)
		}

	def apply[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoManyToOne[T, FID, FPC, F]) =
		{
			val key = column.column.alias
			valueOf[F](key)
		}

	def float[T](column: ColumnInfo[T, java.lang.Float]): java.lang.Float =
		valueOf[java.lang.Float](column.column.name)

	def double[T](column: ColumnInfo[T, java.lang.Double]): java.lang.Double =
		valueOf[java.lang.Double](column.column.name)

	def short[T](column: ColumnInfo[T, java.lang.Short]): java.lang.Short =
		valueOf[java.lang.Short](column.column.name)

	def int[T](column: ColumnInfo[T, java.lang.Integer]): java.lang.Integer =
		valueOf[java.lang.Integer](column.column.name)

	def long[T](column: ColumnInfo[T, java.lang.Long]): java.lang.Long =
		valueOf[java.lang.Long](column.column.name)

	def bigDecimal[T](column: ColumnInfo[T, BigDecimal]): BigDecimal =
		valueOf[BigDecimal](column.column.name)

	def bigInt[T](column: ColumnInfo[T, BigInt]): BigInt =
		valueOf[BigInt](column.column.name)

	def date[T](column: ColumnInfo[T, Date]): Date =
		valueOf[DateTime](column.column.name) match {
			case dt: DateTime => dt.toDate
			case null => null
		}

	def calendar[T](column: ColumnInfo[T, Calendar]): Calendar =
		valueOf[DateTime](column.column.name) match {
			case dt: DateTime => dt.toCalendar(Locale.getDefault)
			case null => null
		}

	def boolean[T](column: ColumnInfo[T, java.lang.Boolean]): java.lang.Boolean =
		valueOf[java.lang.Boolean](column.column.name)

	def mutableHashSet[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableManyToMany[T, FID, FPC, F]): scala.collection.mutable.HashSet[F] = new scala.collection.mutable.HashSet ++ apply(column)
	def mutableLinkedList[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableManyToMany[T, FID, FPC, F]): scala.collection.mutable.LinkedList[F] = new scala.collection.mutable.LinkedList ++ apply(column)

	def mutableHashSet[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableOneToMany[T, FID, FPC, F]): scala.collection.mutable.HashSet[F] = new scala.collection.mutable.HashSet ++ apply(column)
	def mutableLinkedList[T, FID, FPC <: DeclaredIds[FID], F](column: ColumnInfoTraversableOneToMany[T, FID, FPC, F]): scala.collection.mutable.LinkedList[F] = new scala.collection.mutable.LinkedList ++ apply(column)

	/**
	 * the following methods do a conversion
	 */
	protected[mapperdao] def set[T](column: String): Set[T] = valueOf[Any](column) match {
		case t: Traversable[T] => t.toSet
		case i: java.lang.Iterable[T] => i.asScala.toSet
	}
	protected[mapperdao] def seq[T](column: String): Seq[T] = valueOf[Any](column) match {
		case t: Traversable[T] => t.toSeq
		case i: java.lang.Iterable[T] => i.asScala.toSeq
	}

	override def toString = memToString

	protected[mapperdao] def toListOfColumnAndValueTuple(columns: List[ColumnBase]) = columns.map(c => (c, getMEM(c.alias)))
	protected[mapperdao] def toListOfSimpleColumnAndValueTuple(columns: List[SimpleColumn]) = columns.map(c => (c, getMEM(c.alias)))
	protected[mapperdao] def toListOfColumnValue(columns: List[ColumnBase]) = columns.map(c => getMEM(c.alias))
	protected[mapperdao] def isSimpleColumnsChanged[ID, PC <: DeclaredIds[ID], T](tpe: Type[ID, PC, T], from: ValuesMap): Boolean =
		tpe.table.simpleTypeColumnInfos.exists { ci =>
			!Equality.isEqual(apply(ci), from.apply(ci))
		}
}

object ValuesMap {
	protected[mapperdao] def fromEntity[ID, PC <: DeclaredIds[ID], T](typeManager: TypeManager, tpe: Type[ID, PC, T], o: T): ValuesMap = fromEntity(typeManager, tpe, o, true)

	protected[mapperdao] def fromEntity[ID, PC <: DeclaredIds[ID], T](typeManager: TypeManager, tpe: Type[ID, PC, T], o: T, clone: Boolean): ValuesMap =
		{
			val table = tpe.table
			val nm = new scala.collection.mutable.HashMap[String, Any]
			nm ++= table.toColumnAliasAndValueMap(table.columnsWithoutAutoGenerated, o).map {
				case (k, v) =>
					(k,
						typeManager.normalize(if (clone) deepClone(v) else v)
					)
			}

			o match {
				case p: T with Persisted with PC =>
					// include any auto-generated columns
					nm ++= table.toPCColumnAliasAndValueMap(table.simpleTypeAutoGeneratedColumns, p).map(e => (e._1, if (clone) deepClone(e._2) else e._2))
				case _ =>
			}
			new ValuesMap(nm)
		}
	protected[mapperdao] def fromMap(m: scala.collection.Map[String, Any]): ValuesMap =
		{
			val nm = new scala.collection.mutable.HashMap[String, Any]
			nm ++= m
			new ValuesMap(nm)
		}

	private def deepClone[T](o: T): T = o match {
		case t: scala.collection.mutable.Traversable[_] => t.map(e => e).asInstanceOf[T] // copy mutable traversables
		case cal: Calendar => cal.clone.asInstanceOf[T]
		case dt: Date => dt.clone.asInstanceOf[T]
		case _ => o
	}

}