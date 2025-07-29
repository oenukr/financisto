
package ru.orangesoftware.financisto.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.CategoryTree;
import ru.orangesoftware.financisto.test.CategoryBuilder;

public class CategoriesTest extends AbstractDbTest {

    @Test
    public void should_insert_new_category() {
        //given
        /**
         * A
         * - A1
         * -- AA1
         * - A2
         * B
         */
        CategoryBuilder.createDefaultHierarchy(db);
        //when
        Category categoryC = createParentCategory("C");
        db.insertOrUpdate(categoryC, Collections.emptyList());
        //then the new categories get inserted
        /**
         * A ...
         * B
         * C
         */
        CategoryTree<Category> tree = db.getCategoriesTree(false);
        assertEquals(3, tree.size());
        assertEquals("A", tree.getAt(0).getTitle());
        assertEquals("B", tree.getAt(1).getTitle());
        assertEquals("C", tree.getAt(2).getTitle());
        //when
        Category categoryC1 = createChildCategory(categoryC, "C1");
        db.insertOrUpdate(categoryC1, Collections.emptyList());
        //then
        /**
         * A ...
         * B
         * C
         * - C1
         */
        tree = db.getCategoriesTree(false);
        assertEquals(3, tree.size());
        assertEquals("A", tree.getAt(0).getTitle());
        assertEquals("B", tree.getAt(1).getTitle());
        assertEquals("C", tree.getAt(2).getTitle());
        assertEquals(1, tree.getAt(2).children.size());
        assertEquals("C1", tree.getAt(2).children.getAt(0).getTitle());
        //when
        Category categoryD = createParentCategory("D");
        db.insertOrUpdate(categoryD, Collections.emptyList());
        //then
        /**
         * A ...
         * B
         * C ...
         * D
         */
        tree = db.getCategoriesTree(false);
        assertEquals(4, tree.size());
        assertEquals("A", tree.getAt(0).getTitle());
        assertEquals("B", tree.getAt(1).getTitle());
        assertEquals("C", tree.getAt(2).getTitle());
        assertEquals("D", tree.getAt(3).getTitle());
        //when
        Category categoryC2 = createChildCategory(categoryC, "C2");
        db.insertOrUpdate(categoryC2, Collections.emptyList());
        //then
        /**
         * A ...
         * B
         * C
         * - C1
         * - C2
         * D
         */
        tree = db.getCategoriesTree(false);
        assertEquals(4, tree.size());
        assertEquals("A", tree.getAt(0).getTitle());
        assertEquals("B", tree.getAt(1).getTitle());
        assertEquals("C", tree.getAt(2).getTitle());
        assertEquals(2, tree.getAt(2).children.size());
        assertEquals("C1", tree.getAt(2).children.getAt(0).getTitle());
        assertEquals("C2", tree.getAt(2).children.getAt(1).getTitle());
        assertEquals("D", tree.getAt(3).getTitle());
    }

    @Test
    public void should_update_existing_category() {
        //given
        /**
         * A
         * - A1
         * -- AA1
         * - A2
         * B
         */
        Map<String, Category> map = CategoryBuilder.createDefaultHierarchy(db);
        //when
        Category categoryA1 = map.get("A1");
        categoryA1.parent = map.get("B");
        db.insertOrUpdate(categoryA1, Collections.emptyList());
        //then the category should be moved under a new parent
        /**
         * A
         * -A2
         * B
         * -A1
         * --AA1
         */
        CategoryTree<Category> tree = db.getCategoriesTree(false);
        assertEquals(2, tree.size());
        Category a = tree.getAt(0);
        assertEquals("A", a.getTitle());
        assertEquals(1, a.children.size());

        Category a2 = a.children.getAt(0);
        assertEquals("A2", a2.getTitle());
        assertEquals(Category.TYPE_EXPENSE, a2.type);

        Category b = tree.getAt(1);
        assertEquals("B", b.getTitle());
        assertEquals(1, b.children.size());

        Category a1 = b.children.getAt(0);
        assertEquals("A1", a1.getTitle());
        assertEquals(Category.TYPE_INCOME, a1.type);
        assertEquals(1, a1.children.size());

        Category aa1 = a1.children.getAt(0);
        assertEquals("AA1", aa1.getTitle());
        assertEquals(Category.TYPE_INCOME, aa1.type);

        //when
        a1 = db.getCategoryWithParent(categoryA1.getId());
        a1.parent = db.getCategoryWithParent(Category.NO_CATEGORY_ID);
        db.insertOrUpdate(a1, Collections.emptyList());
        //then the category should be moved under a new parent
        /**
         * A
         * -A2
         * B
         * A1
         * -AA1
         */
        tree = db.getCategoriesTree(false);
        assertEquals(3, tree.size());
        a = tree.getAt(0);
        assertEquals("A", a.getTitle());
        assertEquals(1, a.children.size());

        a2 = a.children.getAt(0);
        assertEquals("A2", a2.getTitle());
        assertEquals(Category.TYPE_EXPENSE, a2.type);

        b = tree.getAt(1);
        assertEquals("B", b.getTitle());
        assertFalse(b.hasChildren());

        a1 = tree.getAt(2);
        assertEquals("A1", a1.getTitle());
        assertEquals(Category.TYPE_INCOME, a1.type);
        assertEquals(1, a1.children.size());

        aa1 = a1.children.getAt(0);
        assertEquals("AA1", aa1.getTitle());
        assertEquals(Category.TYPE_INCOME, aa1.type);
    }

    private Category createParentCategory(String title) {
        Category category = new Category();
        category.setTitle(title);
        return category;
    }

    private Category createChildCategory(Category parent, String title) {
        Category category = new Category();
        category.setTitle(title);
        category.parent = parent;
        return category;
    }

}
