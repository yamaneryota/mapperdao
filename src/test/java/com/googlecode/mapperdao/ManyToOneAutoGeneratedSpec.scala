package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * @author kostantinos.kougios
 *
 * 5 Sep 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToOneAutoGeneratedSpec extends SpecificationWithJUnit {
	import ManyToOneAutoGeneratedSpec._
	val (jdbc, driver, mapperDao) = Setup.setupMapperDao(TypeRegistry(PersonEntity, CompanyEntity, HouseEntity))

	"select with skip" in {
		createTables

		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = mapperDao.insert(PersonEntity, person)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.company)), PersonEntity, inserted.id).get must_== Person("Kostas", null, house)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.lives)), PersonEntity, inserted.id).get must_== Person("Kostas", company, null)
	}

	"update to null both FK" in {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted must_== person

		val modified = Person("changed", null, null)
		val updated = update(PersonEntity, inserted, modified)
		updated must_== modified

		val selected = select(PersonEntity, inserted.id).get
		selected must_== updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) must beNone
	}

	"update to null" in {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted must_== person

		val modified = Person("changed", null, inserted.lives)
		val updated = update(PersonEntity, inserted, modified)
		updated must_== modified

		val selected = select(PersonEntity, updated.id).get
		selected must_== updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) must beNone
	}

	"insert" in {
		createTables

		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = mapperDao.insert(PersonEntity, person)
		inserted must_== person
	}

	"insert with existing foreign entity" in {
		createTables

		import mapperDao._
		val company = insert(CompanyEntity, Company("Coders limited"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = insert(PersonEntity, person)
		inserted must_== person

		val selected = select(PersonEntity, inserted.id).get
		selected must_== inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) must beNone
	}

	"select" in {
		createTables

		import mapperDao._
		val company = Company("Coders limited")
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company, house)

		val inserted = insert(PersonEntity, person)

		val selected = select(PersonEntity, inserted.id).get
		selected must_== inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) must beNone
	}

	"select with null FK" in {
		createTables

		import mapperDao._
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", null, house)

		val inserted = insert(PersonEntity, person)

		val selected = select(PersonEntity, inserted.id).get
		selected must_== inserted

		mapperDao.delete(PersonEntity, inserted)
		mapperDao.select(PersonEntity, inserted.id) must beNone
	}

	"update" in {
		createTables

		import mapperDao._
		val company1 = insert(CompanyEntity, Company("Coders limited"))
		val company2 = insert(CompanyEntity, Company("Scala Inc"))
		val house = House("Rhodes,Greece")
		val person = Person("Kostas", company1, house)

		val inserted = insert(PersonEntity, person)
		inserted must_== person

		val modified = Person("changed", company2, inserted.lives)
		val updated = update(PersonEntity, inserted, modified)
		updated must_== modified

		val selected = select(PersonEntity, updated.id).get
		selected must_== updated

		mapperDao.delete(PersonEntity, selected)
		mapperDao.select(PersonEntity, selected.id) must beNone
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
			Setup.database match {
				case "oracle" =>
					Setup.createSeq(jdbc, "CompanySeq")
					Setup.createSeq(jdbc, "HouseSeq")
					Setup.createSeq(jdbc, "PersonSeq")
				case _ =>
			}
		}
}

object ManyToOneAutoGeneratedSpec {
	case class Person(val name: String, val company: Company, val lives: House)
	case class Company(val name: String)
	case class House(val address: String)

	object PersonEntity extends Entity[IntId, Person](classOf[Person]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("PersonSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val company = manytoone(CompanyEntity) to (_.company)
		val lives = manytoone(HouseEntity) to (_.lives)

		def constructor(implicit m) = new Person(name, company, lives) with IntId with Persisted {
			val id: Int = PersonEntity.id
		}
	}

	object CompanyEntity extends Entity[IntId, Company](classOf[Company]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("CompanySeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m) = new Company(name) with IntId with Persisted {
			val id: Int = CompanyEntity.id
		}
	}

	object HouseEntity extends Entity[IntId, House](classOf[House]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("HouseSeq")
			case _ => None
		}) autogenerated (_.id)
		val address = column("address") to (_.address)
		def constructor(implicit m) = new House(address) with IntId with Persisted {
			val id: Int = HouseEntity.id
		}
	}
}