package com.googlecode.mapperdao
import org.specs2.mutable.SpecificationWithJUnit
import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * @author kostantinos.kougios
 *
 * 8 Nov 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToManySimpleTypesSpec extends SpecificationWithJUnit {
	import ManyToManySimpleTypesSpec._
	val typeRegistry = TypeRegistry(ProductEntity)
	val (jdbc, mapperDao, queryDao) = Setup.setupQueryDao(typeRegistry)

	"insert, string based" in {
		createTables("string-based")
		val product = Product("computer", Set("PC", "laptop"))
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted must_== product
	}

	"select, string based" in {
		createTables("string-based")
		val inserted = mapperDao.insert(ProductEntity, Product("computer", Set("PC", "laptop")))
		mapperDao.select(ProductEntity, inserted.id).get must_== inserted
	}

	"update, string based" in {
		createTables("string-based")
		val inserted = mapperDao.insert(ProductEntity, Product("computer", Set("PC", "laptop")))
		val updated = mapperDao.update(ProductEntity, inserted, Product("computer", Set("PC")))
		updated must_== Product("computer", Set("PC"))
		mapperDao.select(ProductEntity, inserted.id).get must_== updated
	}

	def createTables(sql: String) {
		Setup.dropAllTables(jdbc)
		val queries = Setup.queries(this, jdbc)
		queries.update(sql)
		Setup.database match {
			case "oracle" =>
				Setup.createSeq(jdbc, "ProductSeq")
				Setup.createSeq(jdbc, "CategorySeq")
			case _ =>
		}
	}
}

object ManyToManySimpleTypesSpec {
	case class Product(val name: String, val categories: Set[String])

	val se = StringEntity.manyToManyAutoGeneratedPK("Category", "id", "name", Setup.database match {
		case "oracle" => Some("CategorySeq")
		case _ => None
	})

	object ProductEntity extends Entity[IntId, Product](classOf[Product]) {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("ProductSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val categories = manytomany(se) join ("Product_Category", "product_id", "category_id") tostring (_.categories)
		def constructor(implicit m: ValuesMap) = new Product(name, categories) with IntId with Persisted {
			val id: Int = ProductEntity.id
		}
	}
}