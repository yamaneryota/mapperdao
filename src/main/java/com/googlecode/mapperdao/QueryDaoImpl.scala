package com.googlecode.mapperdao
import com.googlecode.mapperdao.exceptions.QueryException
import com.googlecode.mapperdao.drivers.Driver
import java.util.concurrent.ConcurrentHashMap
import com.googlecode.mapperdao.sqlbuilder.SqlBuilder

/**
 * the QueryDao implementation
 *
 * runs queries against the database
 *
 * @author kostantinos.kougios
 *
 * 18 Aug 2011
 */
final class QueryDaoImpl private[mapperdao] (typeRegistry: TypeRegistry, driver: Driver, mapperDao: MapperDaoImpl) extends QueryDao {

	import QueryDao._

	def query[PC, T](queryConfig: QueryConfig, qe: Query.Builder[PC, T]): List[T with PC] =
		{
			if (qe == null) throw new NullPointerException("qe can't be null")
			val r = sqlAndArgs(queryConfig, qe).result
			try {
				val lm = driver.queryForList(queryConfig, qe.entity.tpe, r.sql, r.values)

				queryConfig.multi.runStrategy.run(mapperDao, qe, queryConfig, lm)
			} catch {
				case e =>
					val extra = "\n------\nThe query:%s\nThe arguments:%s\n------\n".format(r.sql, r.values)
					val msg = "An error occured during execution of query %s.\nQuery Information:%s\nIssue:\n%s".format(qe, extra, e.getMessage)
					throw new QueryException(msg, e)
			}
		}

	def count[PC, T](queryConfig: QueryConfig, qe: Query.Builder[PC, T]): Long =
		{
			if (qe == null) throw new NullPointerException("qe can't be null")
			val aliases = new Aliases(typeRegistry)
			val e = qe.entity
			val tpe = e.tpe
			val sql = driver.countSql(aliases, e)
			val q = SqlBuilder.select(driver.escapeNamesStrategy)
			val s = whereAndArgs(q, defaultQueryConfig, qe, aliases)
			val r = q.result
			driver.queryForLong(queryConfig, sql + "\n" + r.sql, r.values)
		}

	private def sqlAndArgs[PC, T](queryConfig: QueryConfig, qe: Query.Builder[PC, T]) =
		{
			val e = qe.entity
			val tpe = e.tpe
			val columns = driver.selectColumns(tpe)

			val aliases = new Aliases(typeRegistry)

			val q = SqlBuilder.select(driver.escapeNamesStrategy)
			val outer = driver.beforeStartOfQuery(q, queryConfig, qe, columns)
			driver.startQuery(q, queryConfig, aliases, qe, columns)
			whereAndArgs(q, queryConfig, qe, aliases)
			driver.endOfQuery(outer, queryConfig, qe)
			outer
		}

	private def whereAndArgs[PC, T](q: SqlBuilder.SqlSelectBuilder, queryConfig: QueryConfig, qe: Query.Builder[PC, T], aliases: Aliases) =
		{
			// iterate through the joins in the correct order
			qe.joins.reverse.foreach { j =>
				val column = j.column
				if (column != null) {
					var foreignEntity = j.foreignEntity
					val joinEntity = j.joinEntity
					j match {
						case join: Query.Join[_, _, _, PC, T] =>
							join.column match {
								case manyToOne: ManyToOne[_, _] =>
									val join = driver.manyToOneJoin(aliases, joinEntity, foreignEntity, manyToOne)
									q.innerJoin(join)
								case oneToMany: OneToMany[_, _] =>
									val join = driver.oneToManyJoin(aliases, joinEntity, foreignEntity, oneToMany)
									q.innerJoin(join)
								case manyToMany: ManyToMany[_, _] =>
									val (leftJoin, rightJoin) = driver.manyToManyJoin(aliases, joinEntity, foreignEntity, manyToMany)
									q.innerJoin(leftJoin)
									q.innerJoin(rightJoin)
								case oneToOneReverse: OneToOneReverse[_, _] =>
									val join = driver.oneToOneReverseJoin(aliases, joinEntity, foreignEntity, oneToOneReverse)
									q.innerJoin(join)
							}
					}
				} else {
					val joined = driver.joinTable(aliases, j)
					q.innerJoin(joined)
				}
			}

			// append the where clause and get the list of arguments
			if (!qe.wheres.isEmpty) {
				val e = driver.queryExpressions(aliases, qe.wheres)
				q.where(e)
			}

			if (!qe.order.isEmpty) {
				val orderColumns = qe.order.map(t => (t._1.column, t._2))

				val orderBySql = driver.orderBy(queryConfig, aliases, orderColumns)
			}
		}
}

