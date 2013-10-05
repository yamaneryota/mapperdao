package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, FunSuite}
import org.joda.time.chrono.ISOChronology

/**
 * Option integration
 *
 * @author kostantinos.kougios
 *
 *         30 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class OptionSuite extends FunSuite with Matchers
{

	case class Category(name: String, parent: Option[Category], linked: Option[Category])

	case class Dog(name: Option[String])

	object CategoryEntity extends Entity[Int, SurrogateIntId, Category]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		// self reference 
		val parent = manytoone(this) foreignkey "parent_id" option (_.parent)
		val linked = onetoone(this) option (_.linked)

		def constructor(implicit m) =
			new Category(name, parent, linked) with Stored
			{
				val id: Int = CategoryEntity.id
			}
	}

	object DogEntity extends Entity[Int, SurrogateIntId, Dog]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") option (_.name)

		def constructor(implicit m) = new Dog(name) with Stored
		{
			val id: Int = DogEntity.id
		}
	}

	val typeManager = new DefaultTypeManager(ISOChronology.getInstance)
	val typeRegistry = TypeRegistry(typeManager, CategoryEntity)

	test("manyToOneOption None=>null") {
		CategoryEntity.parent.columnToValue(Category("x", None, None)) should be(null)
	}

	test("manyToOneOption Some(x)=>x") {
		CategoryEntity.parent.columnToValue(Category("x", Some(Category("y", None, None)), None)) should be === Category("y", None, None)
	}

	test("manyToOne/oneToOne constructor with None") {
		val cat = Category("x", None, None)
		val newCat = CategoryEntity.constructor(ValuesMap.fromType(typeManager, CategoryEntity.tpe, cat))
		newCat should be === cat
	}

	test("manyToOne constructor with Some") {
		val cat = Category("x", Some(Category("y", None, None)), None)
		val newCat = CategoryEntity.constructor(ValuesMap.fromType(typeManager, CategoryEntity.tpe, cat))
		newCat should be === cat
	}

	test("oneToOne None=>null") {
		CategoryEntity.linked.columnToValue(Category("x", None, None)) should be(null)
	}

	test("oneToOneOption Some(x)=>x") {
		CategoryEntity.linked.columnToValue(Category("x", None, Some(Category("y", None, None)))) should be === Category("y", None, None)
	}

	test("oneToOne constructor with Some") {
		val cat = Category("x", None, Some(Category("y", None, None)))
		val newCat = CategoryEntity.constructor(ValuesMap.fromType(typeManager, CategoryEntity.tpe, cat))
		newCat should be === cat
	}
}