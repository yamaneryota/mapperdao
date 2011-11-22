package com.googlecode.mapperdao
import org.specs2.mutable.SpecificationWithJUnit
import utils.TransactionalIntIdCRUD
import com.googlecode.mapperdao.utils.MockTransactionalIntIdCRUD
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * @author kostantinos.kougios
 *
 * 12 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class MockDaosSpec extends SpecificationWithJUnit {
	case class JobPosition(var name: String)
	object JobPositionEntity extends Entity[IntId, JobPosition](classOf[JobPosition]) {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		def constructor(implicit m) = new JobPosition(name) with IntId with Persisted {
			val id: Int = JobPositionEntity.id
		}
	}

	val typeRegistry = TypeRegistry(JobPositionEntity)

	abstract class ActualDao extends TransactionalIntIdCRUD[JobPosition] {
	}

	object TestDao extends ActualDao with MockTransactionalIntIdCRUD[JobPosition] {
		val entity = JobPositionEntity
		val mapperDao = MemoryMapperDao(typeRegistry)
	}

	"mock dao create" in {
		val created = TestDao.create(JobPosition("x"))
		created must_== JobPosition("x")
		TestDao.retrieve(created.id).get must_== JobPosition("x")
	}

	"mock dao update" in {
		val created = TestDao.create(JobPosition("x"))
		val updated = TestDao.update(created, JobPosition("y"))
		updated must_== JobPosition("y")
		TestDao.retrieve(updated.id).get must_== JobPosition("y")
	}

	"mock dao delete" in {
		val created = TestDao.create(JobPosition("x"))
		val deleted = TestDao.delete(created)
		deleted must_== JobPosition("x")
		TestDao.retrieve(created.id) must beNone
	}
}