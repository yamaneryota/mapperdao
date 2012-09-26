package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 2 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class SimpleEntitiesAutoGeneratedSuite extends FunSuite with ShouldMatchers {
	val typeRegistry = TypeRegistry(JobPositionEntity)
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(typeRegistry)

	test("CRUD using sequences") {
		Setup.database match {
			case "postgresql" | "oracle" | "h2" =>
				createJobPositionTable(true)
				val jp = new JobPosition("Developer")
				val inserted = mapperDao.insert(JobPositionEntity, jp)
				inserted should be === jp
				inserted.id should be === 1

				// now load
				val loaded = mapperDao.select(JobPositionEntity, inserted.id).get
				loaded should be === jp

				// update
				loaded.name = "Scala Developer"
				val afterUpdate = mapperDao.update(JobPositionEntity, loaded).asInstanceOf[Persisted]
				afterUpdate.mapperDaoValuesMap(JobPositionEntity.name) should be === "Scala Developer"
				afterUpdate should be === loaded

				val reloaded = mapperDao.select(JobPositionEntity, inserted.id).get
				reloaded should be === loaded

				mapperDao.delete(JobPositionEntity, reloaded)

				mapperDao.select(JobPositionEntity, inserted.id) should be === None
			case "mysql" | "derby" | "sqlserver" =>
		}
	}

	test("CRUD auto-increment") {
		Setup.database match {
			case "oracle" =>
			case _ =>
				createJobPositionTable(false)

				val jp = new JobPosition("Developer")
				val inserted = mapperDao.insert(JobPositionEntity, jp)
				inserted should be === jp

				// now load
				val loaded = mapperDao.select(JobPositionEntity, inserted.id).get
				loaded should be === jp

				// update
				loaded.name = "Scala Developer"
				val afterUpdate = mapperDao.update(JobPositionEntity, loaded).asInstanceOf[Persisted]
				afterUpdate.mapperDaoValuesMap(JobPositionEntity.name) should be === "Scala Developer"
				afterUpdate should be === loaded

				val reloaded = mapperDao.select(JobPositionEntity, inserted.id).get
				reloaded should be === loaded

				mapperDao.delete(JobPositionEntity, reloaded)

				mapperDao.select(JobPositionEntity, inserted.id) should be === None
		}
	}

	def createJobPositionTable(sequences: Boolean) {
		Setup.dropAllTables(jdbc)
		if (sequences) Setup.createMySeq(jdbc)

		Setup.queries(this, jdbc).update(if (sequences) "with-sequences" else "without-sequences")
	}
	case class JobPosition(var name: String)

	object JobPositionEntity extends Entity[IntId, JobPosition] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m) = new JobPosition(name) with IntId  {
			// we force the value to int cause mysql AUTO_GENERATED always returns Long instead of Int
			val id: Int = JobPositionEntity.id
		}
	}
}