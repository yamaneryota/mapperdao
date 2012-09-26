package com.googlecode.mapperdao
import com.googlecode.mapperdao.jdbc.Setup
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
/**
 * @author kostantinos.kougios
 *
 * 16 Dec 2011
 */
@RunWith(classOf[JUnitRunner])
class QueryAutogeneratedSuite extends FunSuite with ShouldMatchers {

	val typeRegistry = TypeRegistry(ComputerEntity)
	implicit val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(typeRegistry)

	// an alias
	val je = ComputerEntity
	val ce = CompanyEntity

	import Query._

	test("query returns id") {
		createTables
		val inserted = mapperDao.insert(ComputerEntity, Computer("job1", None))
		inserted.id should be === 1
		val l = (select from je).toList
		l.head.id should be === 1
	}

	test("query with join returns id") {
		createTables
		val inserted = mapperDao.insert(ComputerEntity, Computer("job1", Some(Company("X Corp"))))
		inserted.id should be === 1
		val l = (select from je join (je, je.company, ce) where ce.name === "X Corp").toList
		val computer = l.head
		computer.id should be === 1
		mapperDao.intIdOf(computer.company.get) should be > 0
	}

	def createTables {
		Setup.dropAllTables(jdbc)
		Setup.database match {
			case "oracle" =>
				Setup.createSeq(jdbc, "companySeq")
				Setup.createSeq(jdbc, "computerSeq")
			case _ =>
		}
		Setup.queries(this, jdbc).update("ddl")
	}

	case class Company(name: String)
	case class Computer(name: String, company: Option[Company])

	object CompanyEntity extends Entity[IntId, Company] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("companySeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m) = new Company(name) with IntId  {
			val id: Int = CompanyEntity.id
		}
	}
	object ComputerEntity extends Entity[IntId, Computer] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("computerSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val company = manytoone(CompanyEntity) option (_.company)

		def constructor(implicit m) = new Computer(name, company) with IntId  {
			val id: Int = ComputerEntity.id
		}
	}
}