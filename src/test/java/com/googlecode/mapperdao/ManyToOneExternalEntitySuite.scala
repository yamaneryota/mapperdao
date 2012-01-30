package com.googlecode.mapperdao
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * Jan 19, 2012
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneExternalEntitySuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, HouseEntity))

	if (Setup.database == "h2") {

		test("delete") {
			createTables

			val person = Person("kostas", House(10, "name10"))
			val inserted = mapperDao.insert(PersonEntity, person)
			mapperDao.delete(PersonEntity, inserted)
			mapperDao.select(PersonEntity, inserted.id) should be(None)
		}

		test("delete with propagate") {
			createTables

			val person = Person("kostas", House(10, "name10"))
			val inserted = mapperDao.insert(PersonEntity, person)
			mapperDao.delete(DeleteConfig(propagate = true), PersonEntity, inserted)
			mapperDao.select(PersonEntity, inserted.id) should be(None)
		}

		test("insert/select") {
			createTables

			val person1 = Person("kostas", House(10, "name10"))
			val inserted1 = mapperDao.insert(PersonEntity, person1)
			inserted1 should be === person1
			val person2 = Person("kostas", House(20, "name20"))
			val inserted2 = mapperDao.insert(PersonEntity, person2)
			inserted2 should be === person2

			mapperDao.select(PersonEntity, inserted1.id).get should be === inserted1
			mapperDao.select(PersonEntity, inserted2.id).get should be === inserted2
		}

		test("update") {
			createTables

			val person = Person("kostas", House(10, "name10"))
			val inserted = mapperDao.insert(PersonEntity, person)
			val toUpdate = Person("kostas", House(20, "name20"))
			val updated = mapperDao.update(PersonEntity, inserted, toUpdate)
			updated should be === toUpdate

			mapperDao.select(PersonEntity, inserted.id).get should be === updated
		}

		test("query for external entity id") {
			createTables

			val person = Person("kostas", House(10, "name10"))
			val inserted = mapperDao.insert(PersonEntity, person)
			val toUpdate = Person("kostas", House(20, "name20"))

			import Query._
			queryDao.query(select from pe where pe.house === person.house) should be === List(person)
		}
	}

	def createTables {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")
	}

	case class Person(val name: String, val house: House)
	case class House(val id: Int, val name: String)

	val pe = PersonEntity

	object PersonEntity extends Entity[IntId, Person](classOf[Person]) {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val house = manytoone(HouseEntity) to (_.house)

		def constructor(implicit m) = new Person(name, house) with Persisted with IntId {
			val id: Int = PersonEntity.id
		}

	}
	object HouseEntity extends ExternalEntity[House](classOf[House]) {
		val id = key("id") to (_.id)

		onInsertManyToOne(PersonEntity.house) { i =>
			PrimaryKeysValues(i.foreign.id)
		}

		onUpdateManyToOne(PersonEntity.house) { u =>
			PrimaryKeysValues(u.foreign.id)
		}

		onSelectManyToOne(PersonEntity.house) { s =>
			s.primaryKeys match {
				case List(id: Int) => House(id, "name" + id)
				case _ => throw new IllegalStateException
			}
		}
	}
}