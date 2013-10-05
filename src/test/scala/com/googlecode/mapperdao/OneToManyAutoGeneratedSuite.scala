package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, FunSuite}
import com.googlecode.concurrent.ExecutorServiceManager

/**
 * this spec is self contained, all entities, mapping are contained in this class
 *
 * @author kostantinos.kougios
 *
 *         12 Jul 2011
 */
@RunWith(classOf[JUnitRunner])
class OneToManyAutoGeneratedSuite extends FunSuite with Matchers
{

	import OneToManyAutoGeneratedSuite._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(List(JobPositionEntity, HouseEntity, PersonEntity))

	test("batch insert") {
		createTables(true)

		val p1 = Person("P1", "P1", Set(House("H1"), House("H2")), 20, Nil)
		val p2 = Person("P2", "P2", Set(House("H3"), House("H4")), 21, Nil)
		val p3 = Person("P3", "P3", Set(House("H5")), 22, Nil)

		val List(i1, i2, i3) = mapperDao.insertBatch(PersonEntity, List(p1, p2, p3))
		i1 should be(p1)
		i2 should be(p2)
		i3 should be(p3)

		mapperDao.select(PersonEntity, i1.id).get should be(i1)
		mapperDao.select(PersonEntity, i2.id).get should be(i2)
		mapperDao.select(PersonEntity, i3.id).get should be(i3)
	}

	test("batch update on inserted") {
		createTables(true)

		val p1 = Person("P1", "P1", Set(House("H1"), House("H2")), 20, Nil)
		val p2 = Person("P2", "P2", Set(House("H3"), House("H4")), 21, Nil)
		val p3 = Person("P3", "P3", Set(House("H5")), 22, Nil)

		val List(i1, i2, i3) = mapperDao.insertBatch(PersonEntity, List(p1, p2, p3))

		val u1 = i1.copy(owns = i1.owns + House("HP1") - House("H1"))
		val u2 = i2.copy(owns = i2.owns + House("HP2") - House("H3"))
		val u3 = i3.copy(owns = i3.owns + House("HP3") - House("H5"))

		val List(up1, up2, up3) = mapperDao.updateBatch(PersonEntity, List((i1, u1), (i2, u2), (i3, u3)))
		up1 should be(u1)
		up2 should be(u2)
		up3 should be(u3)

		mapperDao.select(PersonEntity, up1.id).get should be(up1)
		mapperDao.select(PersonEntity, up2.id).get should be(up2)
		mapperDao.select(PersonEntity, up3.id).get should be(up3)
	}

	test("batch update on selected") {
		createTables(true)

		val p1 = Person("P1", "P1", Set(House("H1"), House("H2")), 20, Nil)
		val p2 = Person("P2", "P2", Set(House("H3"), House("H4")), 21, Nil)
		val p3 = Person("P3", "P3", Set(House("H5")), 22, Nil)

		val List(i1, i2, i3) = mapperDao.insertBatch(PersonEntity, List(p1, p2, p3)).map {
			p =>
				mapperDao.select(PersonEntity, p.id).get
		}

		val u1 = i1.copy(owns = i1.owns + House("HP1") - House("H1"))
		val u2 = i2.copy(owns = i2.owns + House("HP2") - House("H3"))
		val u3 = i3.copy(owns = i3.owns + House("HP3") - House("H5"))

		val List(up1, up2, up3) = mapperDao.updateBatch(PersonEntity, List((i1, u1), (i2, u2), (i3, u3)))
		up1 should be(u1)
		up2 should be(u2)
		up3 should be(u3)

		mapperDao.select(PersonEntity, up1.id).get should be(up1)
		mapperDao.select(PersonEntity, up2.id).get should be(up2)
		mapperDao.select(PersonEntity, up3.id).get should be(up3)
	}

	test("insert and select") {
		createTables(true)

		// noise for the autogenerated id's
		mapperDao.insert(PersonEntity, Person("XX", "X", Set(), 12, List()))

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)

		// we need to apply the same sorting for JobPositions in order for the case classes to be equal because order of list items matters
		val person = Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2))
		val inserted = mapperDao.insert(PersonEntity, person)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.owns, PersonEntity.jobPositions)), PersonEntity, inserted.id).get should be === Person("Kostas", "K", Set(), 16, List())
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.jobPositions)), PersonEntity, inserted.id).get should be === Person("Kostas", "K", inserted.owns, 16, List())
	}

	test("delete with DeleteConfig(true)") {
		createTables(false)

		val inserted = mapperDao.insert(PersonEntity, Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(JobPosition("C++ Developer", 10), JobPosition("Scala Developer", 10))))

		mapperDao.delete(DeleteConfig(true), PersonEntity, inserted)

		jdbc.queryForInt("select count(*) from House") should be === 0
		jdbc.queryForInt("select count(*) from JobPosition") should be === 0
	}

	test("delete with DeleteConfig(true,skip)") {
		createTables(false)

		val inserted = mapperDao.insert(PersonEntity, Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(JobPosition("C++ Developer", 10), JobPosition("Scala Developer", 10))))

		mapperDao.delete(DeleteConfig(true, skip = Set(PersonEntity.jobPositions)), PersonEntity, inserted)
		jdbc.queryForInt("select count(*) from House") should be === 0
		jdbc.queryForInt("select count(*) from JobPosition") should be === 2
	}

	test("select, skip one-to-many") {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)

		// we need to apply the same sorting for JobPositions in order for the case classes to be equal because order of list items matters
		val person = Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2))
		val inserted = mapperDao.insert(PersonEntity, person)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.owns, PersonEntity.jobPositions)), PersonEntity, inserted.id).get should be === Person("Kostas", "K", Set(), 16, List())
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.jobPositions)), PersonEntity, inserted.id).get should be === Person("Kostas", "K", inserted.owns, 16, List())
	}

	test("updating items (immutable)") {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val jp5 = new JobPosition("Graphics Designer", 10)

		// we need to apply the same sorting for JobPositions in order for the case classes to be equal because order of list items matters
		val person = Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3).sortWith(_.name < _.name))
		val inserted = mapperDao.insert(PersonEntity, person)
		inserted should be === person
		var updated = inserted
		def doUpdate(from: Person with SurrogateIntId, to: Person) {
			updated = mapperDao.update(PersonEntity, from, to)
			updated should be === to
			val id = updated.id
			val loaded = mapperDao.select(PersonEntity, id).get
			loaded should be === updated
			loaded should be === to
		}
		doUpdate(updated, new Person("Changed", "K", updated.owns, 18, updated.positions.filterNot(_ == jp1)))
		doUpdate(updated, new Person("Changed Again", "Surname changed too", updated.owns.filter(_.address == "London"), 18, jp5 :: updated.positions.filterNot(jp => jp == jp1 || jp == jp3)))

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) should be(None)
	}

	test("updating items (mutable)") {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions.foreach(_.name = "changed")
		inserted.positions.foreach(_.rank = 5)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated should be === inserted

		val loaded = mapperDao.select(PersonEntity, inserted.id).get
		loaded should be === updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) should be(None)
	}

	test("removing items") {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions = inserted.positions.filterNot(jp => jp == jp1 || jp == jp3)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated should be === inserted

		val loaded = mapperDao.select(PersonEntity, inserted.id).get
		loaded should be === updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) should be(None)
	}

	test("adding items") {
		createTables(true)

		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(new JobPosition("Scala Developer", 10), new JobPosition("Java Developer", 10)))
		val inserted = mapperDao.insert(PersonEntity, person)
		val id = inserted.id

		val loaded = mapperDao.select(PersonEntity, id).get

		// add more elements to the collection
		loaded.positions = (new JobPosition("Groovy Developer", 5) :: new JobPosition("C++ Developer", 8) :: loaded.positions).sortWith(_.name < _.name)
		val updatedPositions = mapperDao.update(PersonEntity, loaded)
		updatedPositions should be === loaded

		val updatedReloaded = mapperDao.select(PersonEntity, id).get
		updatedReloaded should be === updatedPositions

		mapperDao.delete(PersonEntity, updatedReloaded)
		mapperDao.select(PersonEntity, updatedReloaded.id) should be(None)
	}

	test("using already persisted entities") {
		createTables(true)
		Setup.database match {
			case "mysql" =>
				jdbc.update("alter table House modify person_id bigint unsigned")
			case "oracle" =>
				jdbc.update("alter table House modify (person_id null)")
			case "derby" =>
				jdbc.update("alter table House alter person_id null")
			case "sqlserver" =>
				jdbc.update("alter table House alter column person_id int null")
			case _ =>
				jdbc.update("alter table House alter person_id drop not null")
		}
		val h1 = mapperDao.insert(HouseEntity, House("London"))
		val h2 = mapperDao.insert(HouseEntity, House("Rhodes"))
		val h3 = mapperDao.insert(HouseEntity, House("Santorini"))

		val person = new Person("Kostas", "K", Set(h1, h2), 16, List(new JobPosition("Scala Developer", 10), new JobPosition("Java Developer", 10)))
		val inserted = mapperDao.insert(PersonEntity, person)
		val id = inserted.id

		mapperDao.select(PersonEntity, id).get should be === inserted

		val changed = Person("changed", "K", inserted.owns.filterNot(_ == h1), 18, List())
		val updated = mapperDao.update(PersonEntity, inserted, changed)
		updated should be === changed

		mapperDao.select(PersonEntity, id).get should be === updated
	}

	test("CRUD (multi purpose test)") {
		createTables(true)

		val items = for (i <- 1 to 10) yield {
			val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(new JobPosition("Java Developer", 10), new JobPosition("Scala Developer", 10)))
			val inserted = mapperDao.insert(PersonEntity, person)
			inserted should be === person

			val id = inserted.id

			val loaded = mapperDao.select(PersonEntity, id).get
			loaded should be === person

			// update

			loaded.name = "Changed"
			loaded.age = 24
			loaded.positions.head.name = "Java/Scala Developer"
			loaded.positions.head.rank = 123
			val updated = mapperDao.update(PersonEntity, loaded)
			updated should be === loaded

			val reloaded = mapperDao.select(PersonEntity, id).get
			reloaded should be === loaded

			// add more elements to the collection
			reloaded.positions = new JobPosition("C++ Developer", 8) :: reloaded.positions
			val updatedPositions = mapperDao.update(PersonEntity, reloaded)
			updatedPositions should be === reloaded

			val updatedReloaded = mapperDao.select(PersonEntity, id).get
			updatedReloaded should be === updatedPositions

			// remove elements from the collection
			updatedReloaded.positions = updatedReloaded.positions.filterNot(_ == updatedReloaded.positions(1))
			val removed = mapperDao.update(PersonEntity, updatedReloaded)
			removed should be === updatedReloaded

			val removedReloaded = mapperDao.select(PersonEntity, id).get
			removedReloaded should be === removed

			// remove them all
			removedReloaded.positions = List()
			mapperDao.update(PersonEntity, removedReloaded) should be === removedReloaded

			val rereloaded = mapperDao.select(PersonEntity, id).get
			rereloaded should be === removedReloaded
			rereloaded.positions = List(JobPosition("Java Developer %d".format(i), 15 + i))
			rereloaded.name = "final name %d".format(i)
			val reupdated = mapperDao.update(PersonEntity, rereloaded)

			val finalLoaded = mapperDao.select(PersonEntity, id).get
			finalLoaded should be === reupdated
			(id, finalLoaded)
		}
		val ids = items.map(_._1)
		ids.toSet.size should be === ids.size
		// verify all still available
		items.foreach {
			item =>
				mapperDao.select(PersonEntity, item._1).get should be === item._2
		}
	}

	if (Setup.isStress) {
		test("multi-threaded crud") {
			createTables(true)

			ExecutorServiceManager.lifecycle(32, (1 to 100).toList) {
				threadNo1 =>
					for (i <- 1 to 100) {
						val person = Person("Kostas" + i, "surname" + i, Set(House("Rhodes" + i), House("Athens" + i)), i, List(JobPosition("jp" + i, i)))
						val inserted = mapperDao.insert(PersonEntity, person)
						val selected = mapperDao.select(PersonEntity, inserted.id).get
						val up = inserted.copy(owns = inserted.owns + House("new" + i), positions = Nil)
						val updated = mapperDao.update(PersonEntity, selected, up)
						updated should be(up)

						val s = mapperDao.select(PersonEntity, inserted.id).get
						s should be(updated)
					}
			}
		}
	}

	def createTables(cascade: Boolean) {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update(if (cascade) "cascade" else "nocascade")

		Setup.database match {
			case "postgresql" =>
				Setup.createSeq(jdbc, "PersonSeq")
				Setup.createSeq(jdbc, "JobPositionSeq")
				Setup.createSeq(jdbc, "HouseSeq")
			case "oracle" =>
				Setup.createSeq(jdbc, "PersonSeq")
				Setup.createSeq(jdbc, "JobPositionSeq")
				Setup.createSeq(jdbc, "HouseSeq")
			case _ =>
		}
	}
}

object OneToManyAutoGeneratedSuite
{

	/**
	 * ============================================================================================================
	 * the entities
	 * ============================================================================================================
	 */
	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention.
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class JobPosition(var name: String, var rank: Int)
	{
		// this can have any arbitrary methods, no problem!
		def whatRank = rank

		// also any non persisted fields, no prob! It's up to the mappings regarding which fields will be used
		val whatever = 5
	}

	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class Person(var name: String, surname: String, owns: Set[House], var age: Int, var positions: List[JobPosition])

	case class House(address: String)

	/**
	 * ============================================================================================================
	 * Mapping for JobPosition class
	 * ============================================================================================================
	 */

	object JobPositionEntity extends Entity[Int, SurrogateIntId, JobPosition]
	{
		// this is the primary key
		val id = key("id") sequence (Setup.database match {
			case "postgresql" | "oracle" =>
				Some("JobPositionSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		// _.name : JobPosition => Any . Function that maps the column to the value of the object
		val rank = column("rank") to (_.rank)

		def constructor(implicit m) = new JobPosition(name, rank) with Stored
		{
			val id: Int = JobPositionEntity.id
		}
	}

	object HouseEntity extends Entity[Int, SurrogateIntId, House]
	{
		val id = key("id") sequence (Setup.database match {
			case "postgresql" | "oracle" =>
				Some("HouseSeq")
			case _ => None
		}) autogenerated (_.id)
		val address = column("address") to (_.address)

		def constructor(implicit m) = new House(address) with Stored
		{
			val id: Int = HouseEntity.id
		}
	}

	object PersonEntity extends Entity[Int, SurrogateIntId, Person]
	{
		val id = key("id") sequence (Setup.database match {
			case "postgresql" | "oracle" =>
				Some("PersonSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val surname = column("surname") to (_.surname)
		val owns = onetomany(HouseEntity) to (_.owns)
		val age = column("age") to (_.age)
		val jobPositions = onetomany(JobPositionEntity) to (_.positions)

		def constructor(implicit m) = new Person(name, surname, owns, age, m(jobPositions).toList.sortWith(_.name < _.name)) with Stored
		{
			val id: Int = PersonEntity.id
		}
	}

}