package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao.{QueryConfig, Persisted, Entity}
import com.googlecode.mapperdao.jdbc.impl.MapperDaoImpl

/**
 * queries can run using different strategies, i.e. a multi-threaded strategy.
 *
 * @author kostantinos.kougios
 *
 *         6 May 2012
 */
trait QueryRunStrategy
{
	def run[ID, T](mapperDao: MapperDaoImpl, entity: Entity[ID, Persisted, T], queryConfig: QueryConfig, lm: List[DatabaseValues]): List[T with Persisted]
}