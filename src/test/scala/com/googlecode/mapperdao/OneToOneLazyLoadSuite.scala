package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 * 29 Apr 2012
 */
@RunWith(classOf[JUnitRunner])
class OneToOneLazyLoadSuite extends FunSuite with ShouldMatchers {

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(InventoryEntity, ProductEntity))

	if (Setup.database == "h2") {

		val selectConfig = SelectConfig(lazyLoad = LazyLoad.all)

		test("query is lazy") {
			createTables

			val p1 = Product(Inventory(8), 2)
			val p2 = Product(Inventory(10), 3)
			val i1 = mapperDao.insert(ProductEntity, p1)
			val i2 = mapperDao.insert(ProductEntity, p2)

			import Query._
			val l = queryDao.query(QueryConfig(lazyLoad = LazyLoad.all), select from ProductEntity)
			val lp1 = l.head
			val lp2 = l.last
			verifyNotLoaded(lp1)
			verifyNotLoaded(lp2)

			lp1 should be === i1
			lp2 should be === i2
		}

		test("update immutable entity, skip lazy loaded") {
			createTables

			val p = Product(Inventory(8), 2)
			val inserted = mapperDao.insert(ProductEntity, p)

			val selected = mapperDao.select(selectConfig, ProductEntity, inserted.id).get

			val up = Product(Inventory(9), 3)
			val updated = mapperDao.update(UpdateConfig(skip = Set(ProductEntity.inventory)), ProductEntity, selected, up)
			updated should be === up
			val reloaded = mapperDao.select(selectConfig, ProductEntity, inserted.id).get
			reloaded should be === Product(Inventory(8), 3)
		}

		test("update mutable entity") {
			createTables

			val p = Product(Inventory(8), 2)
			val inserted = mapperDao.insert(ProductEntity, p)

			val selected = mapperDao.select(selectConfig, ProductEntity, inserted.id).get

			selected.inventory = Inventory(9)
			val updated = mapperDao.update(ProductEntity, selected)
			updated should be === selected
			val reloaded = mapperDao.select(selectConfig, ProductEntity, inserted.id).get
			reloaded should be === updated
		}

		test("update immutable entity") {
			createTables

			val p = Product(Inventory(8), 2)
			val inserted = mapperDao.insert(ProductEntity, p)

			val selected = mapperDao.select(selectConfig, ProductEntity, inserted.id).get

			val up = Product(Inventory(9), 2)
			val updated = mapperDao.update(ProductEntity, selected, up)
			updated should be === up
			val reloaded = mapperDao.select(selectConfig, ProductEntity, inserted.id).get
			reloaded should be === updated
		}

		test("manually updating them stops lazy loading") {
			createTables

			val p = Product(Inventory(8), 2)
			val inserted = mapperDao.insert(ProductEntity, p)

			val selected = mapperDao.select(selectConfig, ProductEntity, inserted.id).get
			selected.inventory = Inventory(12)
			verifyNotLoaded(selected)
			selected should be === Product(Inventory(12), 2)
			verifyNotLoaded(selected)
		}

		test("select is lazy") {
			createTables

			val p = Product(Inventory(8), 2)
			val inserted = mapperDao.insert(ProductEntity, p)

			val selected = mapperDao.select(selectConfig, ProductEntity, inserted.id).get
			verifyNotLoaded(selected)
			selected should be === inserted
			selected.id should be > 0.toLong
		}
	}

	def verifyNotLoaded(p: Product) {
		val persisted = p.asInstanceOf[Persisted]
		persisted.mapperDaoValuesMap.isLoaded(ProductEntity.inventory) should be(false)
	}

	def createTables =
		{
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("ddl")
		}

	case class Inventory(var stock: Int)
	case class Product(var inventory: Inventory, val x: Int)

	object InventoryEntity extends Entity[NoId, Inventory] {
		val stock = column("stock") to (_.stock)

		def constructor(implicit m) = new Inventory(stock) with NoId
	}

	object ProductEntity extends Entity[SurrogateLongId, Product] {
		val id = key("id") autogenerated (_.id)
		val inventory = onetoonereverse(InventoryEntity) getter ("inventory") to (_.inventory)
		val x = column("x") to (_.x)

		def constructor(implicit m) = new Product(inventory, x) with SurrogateLongId {
			val id: Long = ProductEntity.id
		}
	}
}