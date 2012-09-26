package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 11 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class MemoryMapperDaoSuite extends FunSuite with ShouldMatchers {
	case class JobPosition(var name: String)
	object JobPositionEntity extends Entity[IntId, JobPosition] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		def constructor(implicit m) = new JobPosition(name) with IntId  {
			val id: Int = JobPositionEntity.id
		}
	}
	case class JobPositionKey(val id: String, val name: String)
	object JobPositionEntityKey extends Entity[StringId, JobPositionKey] {
		val id = key("id") to (_.id)
		val name = column("name") to (_.name)
		def constructor(implicit m) = new JobPositionKey(id, name) with StringId
	}

	val typeRegistry = TypeRegistry(JobPositionEntity, JobPositionEntityKey)

	test("insert with autogenerated key") {
		val m = MemoryMapperDao(typeRegistry)

		{
			val inserted = m.insert(JobPositionEntity, JobPosition("x"))
			inserted should be === JobPosition("x")
			inserted.id should be === 1
		}
		{
			val inserted = m.insert(JobPositionEntity, JobPosition("y"))
			inserted should be === JobPosition("y")
			inserted.id should be === 2
		}
	}

	test("insert with key") {
		val m = MemoryMapperDao(typeRegistry)

		{
			val inserted = m.insert(JobPositionEntityKey, JobPositionKey("key1", "x"))
			inserted should be === JobPositionKey("key1", "x")
		}
		{
			val inserted = m.insert(JobPositionEntityKey, JobPositionKey("key2", "y"))
			inserted should be === JobPositionKey("key2", "y")
		}
	}

	test("select with autogenerated key") {
		val m = MemoryMapperDao(typeRegistry)

		{
			val inserted = m.insert(JobPositionEntity, JobPosition("x"))
			m.select(JobPositionEntity, inserted.id).get should be === JobPosition("x")
		}
		{
			val inserted = m.insert(JobPositionEntity, JobPosition("y"))
			m.select(JobPositionEntity, inserted.id).get should be === JobPosition("y")
		}
	}

	test("select with key") {
		val m = MemoryMapperDao(typeRegistry)

		{
			val inserted = m.insert(JobPositionEntityKey, JobPositionKey("key1", "x"))
			m.select(JobPositionEntityKey, "key1").get should be === JobPositionKey("key1", "x")
		}
		{
			val inserted = m.insert(JobPositionEntityKey, JobPositionKey("key2", "y"))
			m.select(JobPositionEntityKey, "key2").get should be === JobPositionKey("key2", "y")
		}
	}

	test("update mutable") {
		val m = MemoryMapperDao(typeRegistry)

		val inserted = m.insert(JobPositionEntity, JobPosition("x"))
		inserted.name = "yy"
		val updated = m.update(JobPositionEntity, inserted)
		updated should be === JobPosition("yy")
		m.select(JobPositionEntity, inserted.id).get should be === updated
	}

	test("update immutable") {
		val m = MemoryMapperDao(typeRegistry)

		val inserted = m.insert(JobPositionEntity, JobPosition("x"))
		val updated = m.update(JobPositionEntity, inserted, JobPosition("yy"))
		updated should be === JobPosition("yy")
		m.select(JobPositionEntity, inserted.id).get should be === updated
	}

	test("remove") {
		val m = MemoryMapperDao(typeRegistry)

		val inserted = m.insert(JobPositionEntity, JobPosition("x"))
		m.delete(JobPositionEntity, inserted)
		m.select(JobPositionEntity, inserted.id) should be(None)
	}

	test("remove by id") {
		val m = MemoryMapperDao(typeRegistry)

		val inserted = m.insert(JobPositionEntity, JobPosition("x"))
		m.delete(JobPositionEntity, inserted.id)
		m.select(JobPositionEntity, inserted.id) should be(None)
	}
}