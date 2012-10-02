package com.googlecode.mapperdao.plugins

import com.googlecode.mapperdao.SelectConfig
import com.googlecode.mapperdao.MapperDaoImpl
import com.googlecode.mapperdao.ColumnInfoTraversableOneToMany
import com.googlecode.mapperdao.Entity
import com.googlecode.mapperdao.EntityMap
import com.googlecode.mapperdao.DatabaseValues
import com.googlecode.mapperdao.DeclaredIds

/**
 * @author kostantinos.kougios
 *
 * 27 May 2012
 */
class OneToManyEntityLazyLoader[ID, PC <: DeclaredIds[ID], T, FID, FPC <: DeclaredIds[FID], F](
	mapperDao: MapperDaoImpl,
	selectConfig: SelectConfig,
	entity: Entity[ID, PC, T],
	down: EntityMap,
	om: DatabaseValues,
	ci: ColumnInfoTraversableOneToMany[T, FID, FPC, F])
		extends LazyLoader {

	private val m = om.map
	def apply =
		{
			val c = ci.column
			val fe = c.foreign.entity
			val ids = entity.tpe.table.primaryKeys.map { pk => m(pk.name) }
			val where = c.foreignColumns.zip(ids)
			val ftpe = fe.tpe
			val fom = mapperDao.driver.doSelect(selectConfig, ftpe, where)
			val v = mapperDao.toEntities(fom, fe, selectConfig, down)
			v
		}
}