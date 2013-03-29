package com.googlecode.mapperdao.jdbc

import java.util.Calendar
import org.scala_tools.time.Imports._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import java.math.BigDecimal
import com.googlecode.mapperdao.utils.Database
import com.googlecode.mapperdao.{DefaultTypeManager, TypeRegistry}

/**
 * @author kostantinos.kougios
 *
 *         12 Jul 2011
 */
@RunWith(classOf[JUnitRunner])
class JdbcSuite extends FunSuite with ShouldMatchers
{
	private val jdbc = Setup.setupJdbc
	val driver = Database.PostgreSql.driver(jdbc, TypeRegistry(), new DefaultTypeManager(), None)

	test("batch insert autogenerated") {
		createTables()
		val now = DateTime.now.withMillisOfDay(0)
		val yesterday = DateTime.now.minusDays(1).withMillisOfDay(0)

		val r = jdbc.batchUpdate(
			batchOptions(true),
			"insert into test_generatedkeys(name,dt) values(?,?)",
			Array(
				Array(
					Jdbc.toSqlParameter(driver, classOf[String], "test1"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], now)
				),
				Array(
					Jdbc.toSqlParameter(driver, classOf[String], "test2"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], yesterday)
				),
				Array(
					Jdbc.toSqlParameter(driver, classOf[String], "test3"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], now)
				)
			))

		toInt(r.keys(0).get(idColumn)) should be(1)
		toInt(r.keys(1).get(idColumn)) should be(2)
		toInt(r.keys(2).get(idColumn)) should be(3)

		val l = jdbc.queryForList("select * from test_generatedkeys order by id")
		fixJdbcMap(l.head) should be(Map("id" -> 1, "name" -> "test1", "dt" -> now))
		fixJdbcMap(l.tail.head) should be(Map("id" -> 2, "name" -> "test2", "dt" -> yesterday))
		fixJdbcMap(l.tail.tail.head) should be(Map("id" -> 3, "name" -> "test3", "dt" -> now))
	}

	test("batch insert") {
		createTables()
		val now = DateTime.now.withMillisOfDay(0)
		val yesterday = DateTime.now.minusDays(1).withMillisOfDay(0)

		val r = jdbc.batchUpdate(
			batchOptions(false),
			"insert into test_insert(id,name,dt) values(?,?,?)",
			Array(
				Array(
					Jdbc.toSqlParameter(driver, classOf[Int], 1),
					Jdbc.toSqlParameter(driver, classOf[String], "test1"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], now)
				),
				Array(
					Jdbc.toSqlParameter(driver, classOf[Int], 5),
					Jdbc.toSqlParameter(driver, classOf[String], "test2"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], yesterday)
				),
				Array(
					Jdbc.toSqlParameter(driver, classOf[Int], 10),
					Jdbc.toSqlParameter(driver, classOf[String], "test3"),
					Jdbc.toSqlParameter(driver, classOf[DateTime], now)
				)
			))

		val l = jdbc.queryForList("select * from test_insert order by id")
		fixJdbcMap(l.head) should be(Map("id" -> 1, "name" -> "test1", "dt" -> now))
		fixJdbcMap(l.tail.head) should be(Map("id" -> 5, "name" -> "test2", "dt" -> yesterday))
		fixJdbcMap(l.tail.tail.head) should be(Map("id" -> 10, "name" -> "test3", "dt" -> now))
	}

	if (Setup.database != "derby") {
		// spring-jdbc for derby seems to read the blob twice, throwing an exception

		test("blob, batch, byte array") {
			createTables()
			val r = jdbc.batchUpdate(
				batchOptions(true),
				"insert into test_blob(name,data) values(?,?)",
				Array(
					Array(
						Jdbc.toSqlParameter(driver, classOf[String], "test1"),
						Jdbc.toSqlParameter(driver, classOf[Array[Byte]], Array[Byte](5, 6, 7))
					),
					Array(
						Jdbc.toSqlParameter(driver, classOf[String], "test2"),
						Jdbc.toSqlParameter(driver, classOf[Array[Byte]], Array[Byte](8, 9, 10))
					),
					Array(
						Jdbc.toSqlParameter(driver, classOf[String], "test3"),
						Jdbc.toSqlParameter(driver, classOf[Array[Byte]], Array[Byte](15, 16, 17))
					)
				))

			toInt(r.keys(0).get(idColumn)) should be(1)
			toInt(r.keys(1).get(idColumn)) should be(2)
			toInt(r.keys(2).get(idColumn)) should be(3)

			val l = jdbc.queryForList("select * from test_blob")
			l.head("data") should be === Array[Byte](5, 6, 7)
			l.tail.head("data") should be === Array[Byte](8, 9, 10)
			l.tail.tail.head("data") should be === Array[Byte](15, 16, 17)
		}

		test("blob, byte array") {
			createTables()
			val data = Array[Byte](5, 10, 15)
			val args = Jdbc.toSqlParameter(
				driver,
				List(
					(classOf[String], "kostas"),
					(classOf[Array[Byte]], data)
				)
			)
			jdbc.updateGetAutoGenerated( """
			insert into test_blob(name,data)
			values(?,?)
			                             """, args)
			jdbc.queryForList("select * from test_blob").head("data") should be === data

			val udata = Array[Byte](15, 20, 25, 30)
			jdbc.update( """
				update test_blob
				set data = ?
				where id=?
			             """, udata, 1)
			jdbc.queryForList("select * from test_blob").head("data") should be === udata
		}
	}

	test("sqlarguments") {
		createTables()
		val dt = DateTime.now.withMillisOfDay(0)
		val now = dt.toCalendar(null)

		val args = Jdbc.toSqlParameter(
			driver,
			List((classOf[Int], 5), (classOf[String], "kostas"), (classOf[Calendar], now))
		)
		jdbc.update( """
			insert into test_insert(id,name,dt)
			values(?,?,?)
		             """, args)

		val r = jdbc.queryForList("select * from test_insert")(0)
		r should be === Map(
			"ID" -> r("id"),
			"NAME" -> "kostas",
			"DT" -> dt
		)
	}

	test("sqlarguments with nulls") {
		createTables()
		val args = Jdbc.toSqlParameter(
			driver,
			List((classOf[Int], 5), (classOf[String], null), (classOf[Calendar], null))
		)
		jdbc.update( """
			insert into test_insert(id,name,dt)
			values(?,?,?)
		             """, args)

		val r = jdbc.queryForList("select * from test_insert")(0)
		r should be === Map(
			"ID" -> r("id"),
			"NAME" -> null,
			"DT" -> null
		)
	}

	test("sequences") {
		Setup.database match {
			case "postgresql" =>
				Setup.dropAllTables(jdbc)
				Setup.createMySeq(jdbc)
				jdbc.update( """
					create table test_insert (
						id int not null default nextval('myseq'),
						name varchar(100) not null,
						primary key (id)
					)""")
				jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kostas')", Array("id")).intKey("id") should be === 1
				jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kougios')", Array("id")).intKey("id") should be === 2
			case "h2" =>
				Setup.dropAllTables(jdbc)
				Setup.createMySeq(jdbc)
				jdbc.update( """
					create table test_insert (
						id int not null default nextval('myseq'),
						name varchar(100) not null,
						primary key (id)
					)""")
				jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kostas')", Array("id")).intKey("SCOPE_IDENTITY()") should be === 1
				jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kougios')", Array("id")).intKey("SCOPE_IDENTITY()") should be === 2
			case "oracle" =>
				Setup.dropAllTables(jdbc)
				Setup.createMySeq(jdbc)
				jdbc.update( """
					create table test_insert (
						id number(10) not null,
						name varchar(100) not null,
						primary key (id)
					)
				             """)
				jdbc.update( """
					create or replace trigger ti_autonumber
					before insert on test_insert for each row
					begin
						select myseq.nextval into :new.id from dual;
					end;
				             """)

				val u1 = jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kostas')", Array("id"))
				u1.intKey("ID") should be === 1
				jdbc.updateGetAutoGenerated("insert into test_insert(name) values('kougios')", Array("id")).intKey("ID") should be === 2
			case "mysql" | "derby" | "sqlserver" => // these don't support sequences
		}
	}

	test("test update with generated keys") {
		createTables()
		val now = Setup.now
		val (f, idColumn) = Setup.database match {
			case "sqlserver" => ((u: UpdateResultWithGeneratedKeys) => u.longKey("GENERATED_KEYS").toInt, "doesn't-matter")
			case "mysql" => ((u: UpdateResultWithGeneratedKeys) => u.longKey("GENERATED_KEY").toInt, "doesn't-matter")
			case "oracle" => ((u: UpdateResultWithGeneratedKeys) => u.intKey("ID"), "ID")
			case "derby" => ((u: UpdateResultWithGeneratedKeys) => u.intKey("1"), "id")
			case "h2" => ((u: UpdateResultWithGeneratedKeys) => u.intKey("SCOPE_IDENTITY()"), "id")
			case _ => ((u: UpdateResultWithGeneratedKeys) => u.intKey("id"), "id")
		}
		f(jdbc.updateGetAutoGenerated("insert into test_generatedkeys(name,dt) values(?,?)", Array(idColumn), "kostas", now)) should be === 1
		f(jdbc.updateGetAutoGenerated("insert into test_generatedkeys(name,dt) values(?,?)", Array(idColumn), "kougios", now)) should be === 2
		val dt3 = (now + 1 second).dateTime.withMillisOfSecond(0)
		f(jdbc.updateGetAutoGenerated("insert into test_generatedkeys(name,dt) values(?,?)", Array(idColumn), "scala", dt3)) should be === 3
		jdbc.updateGetAutoGenerated("insert into test_generatedkeys(name,dt) values(?,?)", Array(idColumn), "java", (now + 2 second).dateTime).rowsAffected should be === 1
		jdbc.queryForMap("select name from test_generatedkeys where id=1").get.string("name") should be === "kostas"
		jdbc.queryForMap("select dt from test_generatedkeys where id=3").get.datetime("dt") should be === dt3
	}

	test("test update method with varargs") {
		createTables()
		val now = Setup.now
		jdbc.update( """
			insert into test_insert(id,name,dt)
			values(?,?,?)
		             """, 5, "kostas", now).rowsAffected should be === 1

		// verify
		val m = jdbc.queryForList("select * from test_insert")(0)
		m.size should be === 3
		val id = m.int("id")
		id should be === 5
		m("name") should be === "kostas"
		m("dt") should be === now
	}

	test("test update method with List") {
		createTables()
		jdbc.update( """
			insert into test_insert(id,name,dt)
			values(?,?,?)
		             """, List(5, "kostas", Calendar.getInstance())).rowsAffected should be === 1

		// verify
		val m = jdbc.queryForList("select * from test_insert")(0)
		m.size should be === 3
		m.int("id") should be === 5
		m("name") should be === "kostas"
	}

	test("test select method with vararg args") {
		createTables()
		jdbc.update( """
			insert into test_insert(id,name,dt)
			values(?,?,?)
		             """, List(5, "kostas", Calendar.getInstance())).rowsAffected should be === 1

		// verify
		val m = jdbc.queryForList("select * from test_insert where id=? and name=?", 5, "kostas")(0)
		m.size should be === 3
		m.int("id") should be === 5
		m("name") should be === "kostas"
	}

	test("test queryForInt varargs") {
		createTables()
		jdbc.update("insert into test_insert(id,name) values(?,?)", 5, "kostas")
		jdbc.queryForInt("select id from test_insert where name=?", "kostas") should be === 5
	}

	test("test queryForInt List") {
		createTables()
		jdbc.update("insert into test_insert(id,name) values(?,?)", 5, "kostas")
		jdbc.queryForInt("select id from test_insert where name=?", List("kostas")) should be === 5
	}

	test("test queryForLong varargs") {
		createTables()
		jdbc.update("insert into test_insert(id,name) values(?,?)", 5, "kostas")
		jdbc.queryForLong("select id from test_insert where name=?", "kostas") should be === 5
	}

	test("test queryForLong List") {
		createTables()
		jdbc.update("insert into test_insert(id,name) values(?,?)", 5, "kostas")
		jdbc.queryForLong("select id from test_insert where name=?", List("kostas")) should be === 5
	}

	def createTables() {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")

		Setup.database match {
			case "oracle" =>
				Setup.createMySeq(jdbc)
			case _ =>
		}
	}

	def toInt(o: Any) = o match {
		case bd: BigDecimal => bd.intValue
		case i: Int => i
		case l: Long => l.toInt
	}

	def fixJdbcMap(m: JdbcMap) = m.toMap.map {
		case (k, v) => (k.toLowerCase, v match {
			case bd: BigDecimal => bd.intValue
			case _ => v
		})
	}.toMap

	def batchOptions(autogenerated: Boolean) = BatchOptions(Setup.database match {
		case "postgresql" | "mysql" =>
			Batch.WithBatch
		case "h2" | "derby" | "oracle" | "sqlserver" =>
			if (autogenerated) Batch.NoBatch else Batch.WithBatch
	}, if (autogenerated) Array("id") else Array())

	def idColumn = Setup.database match {
		case "postgresql" => "id"
		case "sqlserver" => "GENERATED_KEYS"
		case "h2" => "SCOPE_IDENTITY()"
		case "mysql" => "GENERATED_KEY"
		case "derby" => "1"
		case "oracle" => "ID"
	}
}