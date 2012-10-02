package com.googlecode.mapperdao
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 * 13 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class DeclarePrimaryKeysSuite extends FunSuite with ShouldMatchers {
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, PriceEntity))

	test("entity without PK's remove 2 items from collection") {
		createTables
		val p = mapperDao.insert(ProductEntity, Product("x", Set(Price("GBP", 5, 7), Price("EUR", 5, 6), Price("EUR", 7, 8), Price("USD", 9, 10))))
		val newP = new Product(p.title, p.prices.filterNot(_.currency == "EUR"))
		val updated = mapperDao.update(ProductEntity, p, newP)
		updated should be === newP
		val loaded = mapperDao.select(ProductEntity, updated.id).get
		loaded should be === updated
	}

	test("entity without PK's remove all items from collection") {
		createTables
		val p = mapperDao.insert(ProductEntity, Product("x", Set(Price("GBP", 5, 7), Price("EUR", 5, 6), Price("EUR", 7, 8), Price("USD", 9, 10))))
		val newP = new Product(p.title, Set())
		val updated = mapperDao.update(ProductEntity, p, newP)
		updated should be === newP
		val loaded = mapperDao.select(ProductEntity, updated.id).get
		loaded should be === updated
	}

	test("entity without PK's remove 1 item from collection") {
		createTables
		val p = mapperDao.insert(ProductEntity, Product("x", Set(Price("GBP", 5, 7), Price("EUR", 5, 6), Price("EUR", 7, 8), Price("USD", 9, 10))))
		val newP = new Product(p.title, p.prices.filterNot(_.currency == "GBP"))
		val updated = mapperDao.update(ProductEntity, p, newP)
		updated should be === newP
		val loaded = mapperDao.select(ProductEntity, updated.id).get
		loaded should be === updated
	}

	test("entity without PK's add 1 item from collection") {
		createTables
		val p = mapperDao.insert(ProductEntity, Product("x", Set(Price("GBP", 5, 7), Price("EUR", 5, 6), Price("EUR", 7, 8), Price("USD", 9, 10))))
		val newP = new Product(p.title, p.prices + Price("GBP", 6, 8))
		val updated = mapperDao.update(ProductEntity, p, newP)
		updated should be === newP
		val loaded = mapperDao.select(ProductEntity, updated.id).get
		loaded should be === updated
	}

	test("entity without PK's loaded correctly") {
		createTables
		val product = mapperDao.insert(ProductEntity, Product("x", Set(Price("GBP", 5, 7), Price("EUR", 5, 6), Price("EUR", 7, 8), Price("USD", 9, 10))))
		val loaded = mapperDao.select(ProductEntity, product.id).get
		loaded should be === product
	}

	def createTables {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("one-to-many")
		Setup.database match {
			case "oracle" =>
				Setup.createSeq(jdbc, "ProductSeq")
			case _ =>
		}
	}

	case class Product(val title: String, val prices: Set[Price])
	case class Price(val currency: String, val unitPrice: Double, val salePrice: Double)

	object ProductEntity extends Entity[Int, SurrogateIntId, Product] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("ProductSeq")
			case _ => None
		}) autogenerated (_.id)

		val title = column("title") to (_.title)
		val prices = onetomany(PriceEntity) to (_.prices)

		def constructor(implicit m) = new Product(title, prices) with SurrogateIntId {
			val id: Int = ProductEntity.id
		}
	}
	object PriceEntity extends Entity[Unit, NoId, Price] {
		val currency = column("currency") to (_.currency)
		val unitPrice = column("unitprice") to (_.unitPrice)
		val salePrice = column("saleprice") to (_.salePrice)
		declarePrimaryKey(currency)
		declarePrimaryKey(unitPrice)

		def constructor(implicit m) = new Price(currency, unitPrice, salePrice) with NoId
	}
}