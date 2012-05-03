package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.utils.Helpers._

/**
 * @author kostantinos.kougios
 *
 * 2 May 2012
 */
@RunWith(classOf[JUnitRunner])
class LinkSuite extends FunSuite with ShouldMatchers {

	if (Setup.database == "h2") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(CatEntity))

		test("linked, simple entity") {
			val c = Cat(5, "pussy cat", None)
			val linked = mapperDao.link(CatEntity, c)
			linked should be === c

			isPersisted(linked) should be(true)

			val cc = Cat(6, "child cat", Some(c))
			val linkedc = mapperDao.link(CatEntity, cc)
			linkedc should be === cc
			isPersisted(linkedc) should be(true)
		}

		test("linked end to end, simple entity") {
			createTables("simple")
			mapperDao.insert(CatEntity, Cat(5, "pussy cat", None))
			val linked = mapperDao.link(CatEntity, Cat(5, "pussy cat", None))

			val uc = Cat(5, "updated", Some(Cat(7, "child", None)))
			mapperDao.update(CatEntity, linked, uc)
			mapperDao.select(CatEntity, 5).get should be === uc
		}

		test("linked, IntId") {
			val c = CatIId("pussy cat", None)
			val linked = mapperDao.link(CatIIdEntity, c, 5)
			linked should be === c
			linked.id should be === 5

			isPersisted(linked) should be(true)

			val cc = CatIId("child cat", Some(c))
			val linkedc = mapperDao.link(CatIIdEntity, cc, 6)
			linkedc should be === cc
			isPersisted(linkedc) should be(true)
			linkedc.id should be === 6
		}

		test("linked end to end, IntId") {
			createTables("intid")
			val inserted = mapperDao.insert(CatIIdEntity, CatIId("pussy cat", None))
			val linked = mapperDao.link(CatIIdEntity, CatIId("pussy cat", None), inserted.id)

			val uc = CatIId("updated", Some(CatIId("child", None)))
			mapperDao.update(CatIIdEntity, linked, uc)
			mapperDao.select(CatIIdEntity, inserted.id).get should be === uc
		}

		test("linked, LongId") {
			val c = CatLId("pussy cat", None)
			val linked = mapperDao.link(CatLIdEntity, c, 5)
			linked should be === c
			linked.id should be === 5

			isPersisted(linked) should be(true)

			val cc = CatLId("child cat", Some(c))
			val linkedc = mapperDao.link(CatLIdEntity, cc, 6)
			linkedc should be === cc
			isPersisted(linkedc) should be(true)
			linkedc.id should be === 6
		}

		test("linked end to end, LongId") {
			createTables("longid")
			val inserted = mapperDao.insert(CatLIdEntity, CatLId("pussy cat", None))
			val linked = mapperDao.link(CatLIdEntity, CatLId("pussy cat", None), inserted.id)

			val uc = CatLId("updated", Some(CatLId("child", None)))
			mapperDao.update(CatLIdEntity, linked, uc)
			mapperDao.select(CatLIdEntity, inserted.id).get should be === uc
		}

		def createTables(s: String) {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update(s)
		}

	}

	case class Cat(id: Int, name: String, parent: Option[Cat])
	object CatEntity extends SimpleEntity[Cat] {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		val parent = onetoone(this) foreignkey ("parent_id") option (_.parent)

		def constructor(implicit m) = new Cat(id, name, parent) with Persisted
	}

	case class CatIId(name: String, parent: Option[CatIId])
	object CatIIdEntity extends Entity[IntId, CatIId] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val parent = onetoone(this) foreignkey ("parent_id") option (_.parent)

		def constructor(implicit m) = new CatIId(name, parent) with Persisted with IntId {
			val id: Int = CatIIdEntity.id
		}
	}

	case class CatLId(name: String, parent: Option[CatLId])
	object CatLIdEntity extends Entity[LongId, CatLId] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val parent = onetoone(this) foreignkey ("parent_id") option (_.parent)

		def constructor(implicit m) = new CatLId(name, parent) with Persisted with LongId {
			val id: Long = CatLIdEntity.id
		}
	}
}