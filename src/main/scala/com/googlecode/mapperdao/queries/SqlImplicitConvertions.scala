package com.googlecode.mapperdao.queries

import com.googlecode.mapperdao.{ColumnOperation, OneToManyDeclaredPrimaryKeyOperation, Operation, _}
import com.googlecode.mapperdao.queries.v2.{AliasColumn, AliasManyToMany, AliasManyToOne, AliasRelationshipColumn, _}
import com.googlecode.mapperdao.schema.{ColumnInfo, ColumnInfoManyToOne, ColumnInfoOneToOne, ColumnInfoOneToOneReverse, ColumnInfoTraversableManyToMany, ColumnInfoTraversableOneToMany, _}
import org.joda.time._

import scala.language.{existentials, implicitConversions}

/**
 * @author kostantinos.kougios
 *
 *         17 Oct 2012
 */
trait SqlImplicitConvertions
{

	/**
	 * manages simple type expressions
	 */
	protected class Convertor[T, V](t: ColumnInfo[T, V])
	{
		def >(v: V) = new Operation(AliasColumn(t.column), GT, v)

		def >(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), GT, v)

		def >=(v: V) = new Operation(AliasColumn(t.column), GE, v)

		def >=(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), GE, v)

		def <(v: V) = new Operation(AliasColumn(t.column), LT, v)

		def <(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), LT, v)

		def <>(v: V) = new Operation(AliasColumn(t.column), NE, v)

		def <>(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), NE, v)

		def <=(v: V) = new Operation(AliasColumn(t.column), LE, v)

		def <=(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), LE, v)

		def ===(v: V) = new Operation(AliasColumn(t.column), EQ, v) with EqualityOperation

		def ===(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), EQ, v) with EqualityOperation

		def like(v: V) = new Operation(AliasColumn(t.column), LIKE, v)

		def like(v: AliasColumn[V]) = new ColumnOperation(AliasColumn(t.column), LIKE, v)
	}

	implicit def columnInfoToOperableString[T](ci: ColumnInfo[T, String]) = new Convertor(ci)

	implicit def columnInfoToOperableByte[T](ci: ColumnInfo[T, Byte]) = new Convertor(ci)

	implicit def columnInfoToOperableShort[T](ci: ColumnInfo[T, Short]) = new Convertor(ci)

	implicit def columnInfoToOperableInt[T](ci: ColumnInfo[T, Int]) = new Convertor(ci)

	implicit def columnInfoToOperableLong[T](ci: ColumnInfo[T, Long]) = new Convertor(ci)

	implicit def columnInfoToOperableFloat[T](ci: ColumnInfo[T, Float]) = new Convertor(ci)

	implicit def columnInfoToOperableDouble[T](ci: ColumnInfo[T, Double]) = new Convertor(ci)

	implicit def columnInfoToOperableBoolean[T](ci: ColumnInfo[T, Boolean]) = new Convertor(ci)

	implicit def columnInfoToOperableDateTime[T](ci: ColumnInfo[T, DateTime]) = new Convertor(ci)

	implicit def columnInfoToOperableLocalTime[T](ci: ColumnInfo[T, LocalTime]) = new Convertor(ci)

	implicit def columnInfoToOperableLocalDate[T](ci: ColumnInfo[T, LocalDate]) = new Convertor(ci)

	implicit def columnInfoToOperablePeriod[T](ci: ColumnInfo[T, Period]) = new Convertor(ci)

	implicit def columnInfoToOperableDuration[T](ci: ColumnInfo[T, Duration]) = new Convertor(ci)

	implicit def columnInfoToOperableBigInt[T](ci: ColumnInfo[T, BigInt]) = new Convertor(ci)

	implicit def columnInfoToOperableBigDecimal[T](ci: ColumnInfo[T, BigDecimal]) = new Convertor(ci)

	// java
	implicit def columnInfoToOperableJShort[T](ci: ColumnInfo[T, java.lang.Short]) = new Convertor(ci)

	implicit def columnInfoToOperableJInteger[T](ci: ColumnInfo[T, java.lang.Integer]) = new Convertor(ci)

	implicit def columnInfoToOperableJLong[T](ci: ColumnInfo[T, java.lang.Long]) = new Convertor(ci)

	implicit def columnInfoToOperableJFloat[T](ci: ColumnInfo[T, java.lang.Float]) = new Convertor(ci)

	implicit def columnInfoToOperableJDouble[T](ci: ColumnInfo[T, java.lang.Double]) = new Convertor(ci)

	implicit def columnInfoToOperableJBoolean[T](ci: ColumnInfo[T, java.lang.Boolean]) = new Convertor(ci)
}

/**
 * manages many-to-one expressions
 */
trait SqlRelationshipImplicitConvertions
{

	implicit def relationshipColumnToAlias[T, FID, F](v: ColumnInfoRelationshipBase[T, _, FID, F]) =
		new AliasRelationshipColumn[T, FID, F](v.column, Alias.aliasFor(v.column))

	implicit def relationshipAliasColumnToAlias[T, FID, F](alias: (Symbol, ColumnInfoRelationshipBase[T, _, FID, F])) = {
		val (a, v) = alias
		new AliasRelationshipColumn[T, FID, F](v.column, a)
	}

	protected class ConvertorOneToManyDeclaredPrimaryKey[TID, T, FID, F](alias: AliasRelationshipColumn[F, TID, T])
	{
		def ===(v: F) = new OneToManyDeclaredPrimaryKeyOperation[TID, T, FID, F](alias, EQ, v, alias.column.entity.asInstanceOf[EntityBase[TID, T]])

		def <>(v: F) = new OneToManyDeclaredPrimaryKeyOperation[TID, T, FID, F](alias, NE, v, alias.column.entity.asInstanceOf[EntityBase[TID, T]])
	}

	implicit def columnInfoOneToManyForDeclaredPrimaryKeyOperation[TID, T, FID, F](ci: ColumnInfoTraversableOneToManyDeclaredPrimaryKey[FID, F, TID, T]) =
		new ConvertorOneToManyDeclaredPrimaryKey[TID, T, FID, F](AliasRelationshipColumn[F, TID, T](ci.declaredColumnInfo.column, Alias.aliasFor(ci.declaredColumnInfo.column)))

}

trait SqlOneToOneImplicitConvertions
{
	implicit def columnInfoOneToOneCI[T, FID, F](ci: ColumnInfoOneToOne[T, FID, F]) = new AliasOneToOne[FID, F](ci.column, Alias.aliasFor(ci.column))

	implicit def columnInfoOneToOneAlias[T, FID, F](alias: (Symbol, ColumnInfoOneToOne[T, FID, F])) = {
		val (a, ci) = alias
		new AliasOneToOne[FID, F](ci.column, a)
	}
}

trait SqlOneToOneReverseImplicitConvertions
{
	implicit def columnInfoOneToOneReverseCI[T, FID, F](ci: ColumnInfoOneToOneReverse[T, FID, F]) = new AliasOneToOneReverse[FID, F](ci.column, Alias.aliasFor(ci.column))

	implicit def columnInfoOneToOneReverseAlias[T, FID, F](alias: (Symbol, ColumnInfoOneToOneReverse[T, FID, F])) = {
		val (a, ci) = alias
		new AliasOneToOneReverse[FID, F](ci.column, a)
	}
}

trait SqlManyToOneImplicitConvertions
{
	implicit def columnInfoManyToOneCI[T, FID, F](ci: ColumnInfoManyToOne[T, FID, F]) = new AliasManyToOne[T, FID, F](ci.column, Alias.aliasFor(ci.column))

	implicit def columnInfoManyToOneAlias[T, FID, F](alias: (Symbol, ColumnInfoManyToOne[T, FID, F])) = {
		val (a, ci) = alias
		new AliasManyToOne[T, FID, F](ci.column, a)
	}
}

trait SqlManyToManyImplicitConvertions
{
	implicit def columnInfoManyToManyCI[T, FID, F](ci: ColumnInfoTraversableManyToMany[T, FID, F]) =
		new AliasManyToMany[FID, F](ci.column, Alias.aliasFor(ci.column))

	implicit def columnInfoManyToManyAlias[T, FID, F](alias: (Symbol, ColumnInfoTraversableManyToMany[T, FID, F])) = {
		val (a, ci) = alias
		new AliasManyToMany[FID, F](ci.column, a)
	}
}

trait SqlOneToManyImplicitConvertions
{
	implicit def columnInfoOneToManyCI[T, FID, F](ci: ColumnInfoTraversableOneToMany[_, T, FID, F]) = new AliasOneToMany[FID, F](ci.column, Alias.aliasFor(ci.column))

	implicit def columnInfoOneToManyAlias[T, FID, F](alias: (Symbol, ColumnInfoTraversableOneToMany[_, T, FID, F])) = {
		val (a, ci) = alias
		new AliasOneToMany[FID, F](ci.column, a)
	}
}
