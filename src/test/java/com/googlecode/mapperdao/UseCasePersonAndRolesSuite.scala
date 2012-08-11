package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scala_tools.time.Imports._
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 10 Aug 2012
 */
@RunWith(classOf[JUnitRunner])
class UseCasePersonAndRolesSuite extends FunSuite with ShouldMatchers {

	if (Setup.database == "postgresql") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(
			TypeRegistry(
				RoleTypeEntity,
				PersonEntity,
				SinglePartyRoleEntity,
				InterPartyRelationshipEntity
			)
		)

		// aliases for queries
		val pe = PersonEntity
		val spr = SinglePartyRoleEntity
		val rte = RoleTypeEntity
		val ipr = InterPartyRelationshipEntity

		// test data
		val roleType1 = RoleType("Scala Developer", Some("A Scala Software Developer"))
		val roleType2 = RoleType("Java Developer", Some("A Java Software Developer"))
		val roleType3 = RoleType("Groovy Developer", Some("A Groovy Software Developer"))

		val from = DateTime.now
		val to = DateTime.now.plusDays(1)

		// create test roles
		def persistRoles = (
			mapperDao.insert(RoleTypeEntity, roleType1),
			mapperDao.insert(RoleTypeEntity, roleType2),
			mapperDao.insert(RoleTypeEntity, roleType3)
		)

		// create test people
		def people(role1: RoleType, role2: RoleType, role3: RoleType) = (
			mapperDao.insert(PersonEntity,
				Person("kostas.kougios", "kostas", "kougios", Set(
					SinglePartyRole(
						role1,
						Some(from),
						Some(to)
					),
					SinglePartyRole(
						role2,
						Some(from),
						None
					)
				))
			),
				mapperDao.insert(PersonEntity,
					Person("some.other", "some", "other", Set(
						SinglePartyRole(
							role1,
							None,
							None
						),
						SinglePartyRole(
							role3,
							None,
							Some(from)
						)
					))
				)
		)
		test("interpartyrelationship") {
			createTables()
			val (role1, role2, role3) = persistRoles
			val (person1, person2) = people(role1, role2, role3)
			val ipr1 = mapperDao.insert(InterPartyRelationshipEntity, InterPartyRelationship(person1, person2, Some(from), None))
			val ipr2 = mapperDao.insert(InterPartyRelationshipEntity, InterPartyRelationship(person2, person1, Some(from), Some(to)))

			import Query._

			// various queries to get it back

			val selected = queryDao.querySingleResult(
				(
					select
					from ipr
					where ipr.from === person1 and ipr.to === person2
				)
			).get

			selected should be === ipr1

			(
				select
				from ipr
				where ipr.from === person1 and ipr.to === person2
			).toSet(queryDao) should be === Set(ipr1)

			(
				select
				from ipr
				where ipr.from === person2 and ipr.to === person1
			).toSet(queryDao) should be === Set(ipr2)

			// now update
			val upd = selected.copy(to = person1, toDate = Some(to))
			val updated = mapperDao.update(InterPartyRelationshipEntity, selected, upd)
			updated should be === upd

			queryDao.querySingleResult(
				(
					select
					from ipr
					where ipr.from === person1 and ipr.to === person1
				)
			).get should be === updated
		}

		test("crud") {
			createTables()
			val (role1, role2, role3) = persistRoles
			val (inserted1, _) = people(role1, role2, role3)

			val selected1 = mapperDao.select(PersonEntity, inserted1.id).get
			selected1 should be === inserted1

			val updated1 = mapperDao.update(
				PersonEntity,
				selected1,
				Person("kostas.kougios", "kostas", "updated",
					selected1.singlePartyRoles.filter(_.roleType == role2) +
						SinglePartyRole(
							role3,
							Some(from),
							Some(to)
						)
				)
			)
			val selectedAgain1 = mapperDao.select(PersonEntity, inserted1.id).get
			selectedAgain1 should be === updated1
		}

		test("querying") {
			createTables()
			val (role1, role2, role3) = persistRoles
			val inserted1 = mapperDao.insert(PersonEntity,
				Person("kostas.kougios", "kostas", "kougios", Set(
					SinglePartyRole(
						role1,
						Some(from),
						Some(to)
					),
					SinglePartyRole(
						role2,
						Some(from),
						None
					)
				)))
			val inserted2 = mapperDao.insert(
				PersonEntity,
				Person("some.other", "some", "other", Set(
					SinglePartyRole(
						role1,
						None,
						None
					),
					SinglePartyRole(
						role3,
						None,
						Some(from)
					)
				))
			)

			import Query._

			(
				select
				from pe
			).toSet(queryDao) should be === Set(inserted1, inserted2)

			(
				select
				from pe
				join (pe, pe.singlePartyRoles, spr)
				where spr.roleType === role2
			).toSet(queryDao) should be === Set(inserted1)

			(
				select
				from pe
				join (pe, pe.singlePartyRoles, spr)
				where spr.roleType === role3
			).toSet(queryDao) should be === Set(inserted2)

			(
				select
				from pe
				join (pe, pe.singlePartyRoles, spr)
				join (spr, spr.roleType, rte)
				where rte.name === "Java Developer"
			).toSet(queryDao) should be === Set(inserted1)
		}

		def createTables() = {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}
	}

	case class Person(id: String, firstName: String, lastName: String, singlePartyRoles: Set[SinglePartyRole])
	case class SinglePartyRole(roleType: RoleType, fromDate: Option[DateTime], toDate: Option[DateTime])
	case class RoleType(name: String, description: Option[String])
	case class InterPartyRelationship(from: Person, to: Person, fromDate: Option[DateTime], toDate: Option[DateTime])

	object RoleTypeEntity extends SimpleEntity[RoleType] {
		val name = key("name") to (_.name)
		val description = column("description") option (_.description)

		def constructor(implicit m: ValuesMap) = {
			new RoleType(name, description) with Persisted
		}
	}

	object PersonEntity extends SimpleEntity[Person] {
		val id = key("id") to (_.id)
		val firstName = column("firstname") to (_.firstName)
		val lastName = column("lastname") to (_.lastName)
		val singlePartyRoles = onetomany(SinglePartyRoleEntity) to (_.singlePartyRoles)

		def constructor(implicit m: ValuesMap) = {
			new Person(id, firstName, lastName, singlePartyRoles) with Persisted
		}
	}

	object SinglePartyRoleEntity extends SimpleEntity[SinglePartyRole] {
		val roleType = manytoone(RoleTypeEntity) to (_.roleType)
		val fromDate = column("fromDate") option (_.fromDate)
		val toDate = column("toDate") option (_.toDate)

		declarePrimaryKey(roleType)

		def constructor(implicit m: ValuesMap) = new SinglePartyRole(roleType, fromDate, toDate) with Persisted
	}

	object InterPartyRelationshipEntity extends SimpleEntity[InterPartyRelationship] {
		val from = manytoone(PersonEntity) foreignkey ("from_id") to (_.from)
		val to = manytoone(PersonEntity) foreignkey ("to_id") to (_.to)
		val fromDate = column("fromDate") option (_.fromDate)
		val toDate = column("toDate") option (_.toDate)

		declarePrimaryKey(from)
		declarePrimaryKey(to)

		def constructor(implicit m: ValuesMap) = new InterPartyRelationship(from, to, fromDate, toDate) with Persisted
	}
}
