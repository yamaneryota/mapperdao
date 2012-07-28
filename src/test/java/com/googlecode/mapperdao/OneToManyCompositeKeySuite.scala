package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 24 Jul 2012
 */
@RunWith(classOf[JUnitRunner])
class OneToManyCompositeKeySuite extends FunSuite with ShouldMatchers {

	val database = Setup.database
	if (database != "h2") {
		implicit val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(HouseEntity, DoorEntity))

		// aliases
		val he = HouseEntity
		val de = DoorEntity

		test("query") {
			createTables()

			noise
			noise

			val h1 = House("London", Set(Door("kitchen"), Door("bathroom")))
			val h2 = House("London", Set(Door("balcony"), Door("bathroom")))

			mapperDao.insert(HouseEntity, h1)
			mapperDao.insert(HouseEntity, h2)

			import Query._

			(select
				from he
				where he.address === "London"
			).toSet should be === Set(h1, h2)

			(select
				from he
				join (he, he.doors, de)
				where he.address === "London" and de.location === "balcony"
			).toList should be === List(h2)

			(select
				from he
				join (he, he.doors, de)
				where he.address === "London" and de.location === "bathroom"
			).toSet should be === Set(h1, h2)
		}

		test("insert, select and delete") {
			createTables()

			noise
			noise

			val h = House("London", Set(Door("kitchen"), Door("bathroom")))

			val inserted = mapperDao.insert(HouseEntity, h)
			inserted should be === h

			mapperDao.select(HouseEntity, List(inserted.id, inserted.address)).get should be === inserted
			mapperDao.delete(HouseEntity, inserted)
			mapperDao.select(HouseEntity, List(inserted.id, inserted.address)) should be === None
		}

		test("update, remove") {
			createTables()

			noise
			noise

			val inserted = mapperDao.insert(HouseEntity, House("London", Set(Door("kitchen"), Door("bathroom"))))
			val upd = inserted.copy(doors = inserted.doors.filter(_.location == "kitchen"))
			val updated = mapperDao.update(HouseEntity, inserted, upd)
			updated should be === upd
			val selected = mapperDao.select(HouseEntity, List(inserted.id, inserted.address)).get
			selected should be === updated
		}

		test("update, add") {
			createTables()

			noise
			noise

			val inserted = mapperDao.insert(HouseEntity, House("London", Set(Door("kitchen"))))
			val upd = inserted.copy(doors = inserted.doors + Door("bathroom"))
			val updated = mapperDao.update(HouseEntity, inserted, upd)
			updated should be === upd
			val selected = mapperDao.select(HouseEntity, List(inserted.id, inserted.address)).get
			selected should be === updated
		}

		def noise = mapperDao.insert(HouseEntity, House("Paris", Set(Door("livingroom"), Door("balcony"))))
		def createTables() =
			{
				Setup.dropAllTables(jdbc)
				Setup.queries(this, jdbc).update("ddl")
				Setup.createSeq(jdbc, "HouseSeq")
			}
	}

	case class House(address: String, doors: Set[Door])
	case class Door(location: String)

	object HouseEntity extends Entity[IntId, House] {
		val id = key("id") sequence (
			if (database == "oracle") Some("HouseSeq") else None
		) autogenerated (_.id)
		val address = key("address") to (_.address)
		val doors = onetomany(DoorEntity) to (_.doors)

		def constructor(implicit m) = new House(address, doors) with IntId with Persisted {
			val id: Int = HouseEntity.id
		}
	}

	object DoorEntity extends SimpleEntity[Door] {
		val location = column("location") to (_.location)

		declarePrimaryKey(location)

		def constructor(implicit m) = new Door(location) with Persisted
	}
}