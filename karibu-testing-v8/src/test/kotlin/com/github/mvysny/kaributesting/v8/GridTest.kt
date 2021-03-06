package com.github.mvysny.kaributesting.v8

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectThrows
import com.github.mvysny.karibudsl.v8.addColumnFor
import com.github.mvysny.karibudsl.v8.getColumnBy
import com.github.mvysny.karibudsl.v8.grid
import com.vaadin.data.provider.ListDataProvider
import com.vaadin.shared.MouseEventDetails
import com.vaadin.ui.CheckBox
import com.vaadin.ui.Grid
import com.vaadin.ui.TextField
import com.vaadin.ui.UI
import com.vaadin.ui.renderers.ButtonRenderer
import com.vaadin.ui.renderers.ComponentRenderer
import kotlin.test.expect
import kotlin.test.fail

class GridTest : DynaTest({

    beforeEach { MockVaadin.setup() }
    afterEach { MockVaadin.tearDown() }

    test("_size") {
        expect(20) { ListDataProvider<TestPerson>((0 until 20).map { TestPerson("name $it", it) })._size() }
    }

    test("_get") {
        expect("name 5") { ListDataProvider<TestPerson>((0 until 20).map { TestPerson("name $it", it) })._get(5).name }
        expectThrows(AssertionError::class, "Requested to get row 30 but the data provider only has 20 rows") {
            ListDataProvider<TestPerson>((0 until 20).map { TestPerson("name $it", it) })._get(30)
        }
    }

    test("_findAll") {
        val list = (0 until 20).map { TestPerson("name $it", it) }
        expect(list) { ListDataProvider<TestPerson>(list)._findAll() }
    }

    test("_dump") {
        val dp = ListDataProvider<TestPerson>((0 until 7).map { TestPerson("name $it", it) })
        val grid = Grid<TestPerson>(TestPerson::class.java)
        grid.removeColumn("name")
        grid.addColumn({ it.name }, { it.toUpperCase() }).apply { id = "name"; caption = "The Name" }
        grid.dataProvider = dp
        grid.setColumns("name", "age")
        expect("--[The Name]-[Age]--\n--and 7 more\n") { grid._dump(0 until 0) }
        expect("--[The Name]-[Age]--\n0: NAME 0, 0\n1: NAME 1, 1\n2: NAME 2, 2\n3: NAME 3, 3\n4: NAME 4, 4\n--and 2 more\n") { grid._dump(0 until 5) }
        expect("--[The Name]-[Age]--\n0: NAME 0, 0\n1: NAME 1, 1\n2: NAME 2, 2\n3: NAME 3, 3\n4: NAME 4, 4\n5: NAME 5, 5\n6: NAME 6, 6\n") { grid._dump(0 until 20) }
    }

    test("_dump shows sorting indicator") {
        val dp = ListDataProvider<TestPerson>((0 until 7).map { TestPerson("name $it", it) })
        val grid = UI.getCurrent().grid<TestPerson>(dataProvider = dp) {
            addColumnFor(TestPerson::name)
            addColumnFor(TestPerson::age)
            sort(TestPerson::name.asc, TestPerson::age.desc)
        }
        expect("--[Name]v-[Age]^--\n--and 7 more\n") { grid._dump(0 until 0) }
    }

    test("expectRow()") {
        val grid = Grid<TestPerson>(TestPerson::class.java)
        grid.dataProvider = ListDataProvider<TestPerson>((0 until 7).map { TestPerson("name $it", it) })
        grid.expectRows(7)
        grid.expectRow(0, "name 0", "0")
    }

    test("lookup finds components in header") {
        val grid = Grid<TestPerson>(TestPerson::class.java)
        grid.getHeaderRow(0).getCell(TestPerson::age.name).component = TextField("Foo!")
        expect("Foo!") { grid._get<TextField>().caption }
    }

    group("click item") {
        test("item click listener is called") {
            val grid = Grid<TestPerson>().apply {
                addColumnFor(TestPerson::name)
                addColumnFor(TestPerson::age)
                setItems((0..10).map { TestPerson("name $it", it) })
            }
            var called = false
            grid.addItemClickListener { e ->
                expect(grid.getColumnBy(TestPerson::name)) { e.column }
                expect("name 5") { e.item.name }
                expect(5) { e.rowIndex }
                expect(false) { e.mouseEventDetails.isCtrlKey }
                called = true
            }
            grid._clickItem(5)
            expect(true) { called }
        }
        test("mouse event details are properly passed to the listener") {
            val grid = Grid<TestPerson>().apply {
                addColumnFor(TestPerson::name)
                addColumnFor(TestPerson::age)
                setItems((0..10).map { TestPerson("name $it", it) })
            }
            var called = false
            grid.addItemClickListener { e ->
                expect(grid.getColumnBy(TestPerson::name)) { e.column }
                expect("name 5") { e.item.name }
                expect(5) { e.rowIndex }
                expect(true) { e.mouseEventDetails.isCtrlKey }
                called = true
            }
            grid._clickItem(5, mouseEventDetails = MouseEventDetails().apply { isCtrlKey = true })
            expect(true) { called }
        }
    }

    test("click renderer") {
        var called = false
        val grid = Grid<TestPerson>().apply {
            addColumnFor(TestPerson::name) {
                setRenderer(ButtonRenderer<TestPerson> { e ->
                    called = true
                    expect("name 8") { e.item.name }
                    expect("name") { e.column.id }
                    expect(true) { e.mouseEventDetails.isCtrlKey }
                })
            }
            addColumnFor(TestPerson::age)
            setItems((0..10).map { TestPerson("name $it", it) })
        }
        grid._clickRenderer(8, "name", MouseEventDetails().apply { isCtrlKey = true })
        expect(true) { called }
    }

    test("get component") {
        val grid = Grid<TestPerson>().apply {
            addColumn({ it -> CheckBox(it.name) }, ComponentRenderer()).id = "foo"
            setItems((0..10).map { TestPerson("name $it", it) })
        }
        expect("name 3") { (grid._getComponentAt(3, "foo") as CheckBox).caption }
    }

    test("sorting") {
        val grid = Grid<TestPerson>().apply {
            addColumnFor(TestPerson::name)
            addColumnFor(TestPerson::age)
            setItems((0..10).map { TestPerson("name $it", it) })
        }
        grid.sort(TestPerson::age.desc)
        expect((0..10).map { TestPerson("name $it", it) }.reversed()) { grid._findAll() }
        expect(10) { grid._get(0).age }
        expect(0) { grid._get(10).age }
    }
})

data class TestPerson(val name: String, val age: Int)
