package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.jdbc.Transaction

/**
 * @author kostantinos.kougios
 *
 * 6 May 2012
 */
@RunWith(classOf[JUnitRunner])
class MultiThreadedQuerySuite extends FunSuite with ShouldMatchers {

	if (Setup.database == "h2" || Setup.database == "postgresql") {
		val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))
		val txManager = Transaction.transactionManager(jdbc)

		test("parallel query") {
			createTables

			val tx = Transaction.default(txManager)
			val (attrs, products) = tx { () =>
				val attrs = for (i <- 1 to 20) yield {
					mapperDao.insert(AttributeEntity, Attribute("a" + i, "v" + i))
				}
				val products = for (i <- 1 to 10000) yield {
					val idx = i % 19
					mapperDao.insert(ProductEntity, Product("product" + i, Set(attrs(idx), attrs(idx + 1))))
				}
				(attrs, products)
			}

			val start = System.currentTimeMillis
			import Query._
			val loaded = queryDao.query(
				QueryConfig(multi = MultiThreadedConfig.Multi),
				select from ProductEntity orderBy (ProductEntity.name, desc)
			)
			val dt = System.currentTimeMillis - start
			println("dt: " + dt)
			loaded.toSet should be === products.toSet
			loaded.foreach { p =>
				p match {
					case pp: Persisted =>
						pp.mapperDaoMock should be(false)
						pp.attributes.foreach { a =>
							a match {
								case pa: Persisted =>
									pa.mapperDaoMock should be(false)
							}
						}
				}
			}
			// check the order
			loaded.head.name should be === "product9999"
			loaded.tail.head.name should be === "product9998"
			loaded.tail.tail.head.name should be === "product9997"
			loaded.tail.tail.tail.head.name should be === "product9996"
		}

		def createTables {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}
	}

	case class Product(val name: String, val attributes: Set[Attribute])
	case class Attribute(val name: String, val value: String)

	object ProductEntity extends Entity[SurrogateIntId, Product] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val attributes = manytomany(AttributeEntity) to (_.attributes)

		def constructor(implicit m) = new Product(name, attributes) with SurrogateIntId {
			val id: Int = ProductEntity.id
		}
	}

	object AttributeEntity extends Entity[SurrogateIntId, Attribute] {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		val value = column("value") to (_.value)

		def constructor(implicit m) = new Attribute(name, value) with SurrogateIntId {
			val id: Int = AttributeEntity.id
		}
	}
}