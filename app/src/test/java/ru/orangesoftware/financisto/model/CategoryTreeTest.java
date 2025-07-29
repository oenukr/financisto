package ru.orangesoftware.financisto.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CategoryTreeTest {

    private CategoryTree<Category> tree;
    private Category category;

    @Before
    public void setUp() throws Exception {
        category = createIncomeCategory(1, 1, 8);
        category.setTitle("ZZZ");
        category.addChild(createCategory(2, 2, 3));
        category.addChild(createCategory(3, 4, 5));
        category.addChild(createCategory(4, 6, 7));
        Category b = createCategory(5, 9, 16);
        b.setTitle("YYY");
        Category b1 = createExpenseCategory(6, 11, 15);
        b.addChild(b1);
        b1.addChild(createCategory(7, 11, 12));
        b1.addChild(createCategory(8, 13, 14));
        Category c = createIncomeCategory(9, 17, 18);
        c.setTitle("XXX");
        tree = new CategoryTree<>();
        tree.add(category);
        tree.add(b);
        tree.add(c);
    }

    private Category createIncomeCategory(long id, int left, int right) {
        Category c = createCategory(id, left, right);
        c.makeThisCategoryIncome();
        return c;
    }

    private Category createExpenseCategory(long id, int left, int right) {
        Category c = createCategory(id, left, right);
        c.makeThisCategoryExpense();
        return c;
    }

    private Category createCategory(long id, int left, int right) {
        Category c = new Category(id);
        c.left = left;
        c.right = right;
        return c;
    }

    @Test
    public void shouldCheckThatAddingChildCategoryAutomaticallyPopulatesCorrectType() {
        assertTypesOfAllNodes();
    }

    private void assertTypesOfAllNodes() {
        for (Category c : tree) {
            assertTheSameTypeForAllChildren(c);
        }
    }

    private void assertTheSameTypeForAllChildren(Category parent) {
        if (parent.hasChildren()) {
            for (Category child : parent.children) {
                assertEquals("Parent and child should be of the same type", parent.type, child.type);
                assertTheSameTypeForAllChildren(child);
            }
        }
    }

    @Test
    public void shouldMoveCategoryWithNoChildrenUpCorrectly() {
        CategoryTree<Category> tree = category.children;
        assertFalse(tree.moveCategoryUp(tree.size()));
        assertFalse(tree.moveCategoryUp(-1));
        assertFalse(tree.moveCategoryUp(0));
        assertTrue(tree.moveCategoryUp(1));
        Category a2 = tree.getAt(0);
        assertEquals(3, a2.getId());
        assertEquals(2, a2.left);
        assertEquals(3, a2.right);
        Category a1 = tree.getAt(1);
        assertEquals(2, a1.getId());
        assertEquals(4, a1.left);
        assertEquals(5, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithNoChildrenDownCorrectly() {
        CategoryTree<Category> tree = category.children;
        assertFalse(tree.moveCategoryDown(tree.size()));
        assertFalse(tree.moveCategoryDown(tree.size() - 1));
        assertFalse(tree.moveCategoryDown(-1));
        assertTrue(tree.moveCategoryDown(0));
        Category a2 = tree.getAt(0);
        assertEquals(3, a2.getId());
        assertEquals(2, a2.left);
        assertEquals(3, a2.right);
        Category a1 = tree.getAt(1);
        assertEquals(2, a1.getId());
        assertEquals(4, a1.left);
        assertEquals(5, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithChildrenUpCorrectly() {
        CategoryTree<Category> tree = this.tree;
        assertTrue(tree.moveCategoryUp(1));
        Category a = tree.getAt(1);
        assertEquals(1, a.getId());
        assertEquals(9, a.left);
        assertEquals(16, a.right);
        Category b = tree.getAt(0);
        assertEquals(5, b.getId());
        assertEquals(1, b.left);
        assertEquals(8, b.right);
        Category b1 = b.children.getAt(0);
        assertEquals(6, b1.getId());
        assertEquals(2, b1.left);
        assertEquals(7, b1.right);
        Category a1 = a.children.getAt(0);
        assertEquals(2, a1.getId());
        assertEquals(10, a1.left);
        assertEquals(11, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithChildrenDownCorrectly() {
        CategoryTree<Category> tree = this.tree;
        assertTrue(tree.moveCategoryDown(0));
        Category a = tree.getAt(1);
        assertEquals(1, a.getId());
        assertEquals(9, a.left);
        assertEquals(16, a.right);
        Category b = tree.getAt(0);
        assertEquals(5, b.getId());
        assertEquals(1, b.left);
        assertEquals(8, b.right);
        Category b1 = b.children.getAt(0);
        assertEquals(6, b1.getId());
        assertEquals(2, b1.left);
        assertEquals(7, b1.right);
        Category a1 = a.children.getAt(0);
        assertEquals(2, a1.getId());
        assertEquals(10, a1.left);
        assertEquals(11, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithNoChildrenToTopCorrectly() {
        CategoryTree<Category> tree = category.children;
        assertFalse(tree.moveCategoryToTheTop(tree.size()));
        assertFalse(tree.moveCategoryToTheTop(-1));
        assertFalse(tree.moveCategoryToTheTop(0));
        assertTrue(tree.moveCategoryToTheTop(2));
        Category a3 = tree.getAt(0);
        assertEquals(4, a3.getId());
        assertEquals(2, a3.left);
        assertEquals(3, a3.right);
        Category a1 = tree.getAt(1);
        assertEquals(2, a1.getId());
        assertEquals(4, a1.left);
        assertEquals(5, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithNoChildrenToBottomCorrectly() {
        CategoryTree<Category> tree = category.children;
        assertFalse(tree.moveCategoryToTheBottom(tree.size()));
        assertFalse(tree.moveCategoryToTheBottom(tree.size() - 1));
        assertFalse(tree.moveCategoryToTheBottom(-1));
        assertTrue(tree.moveCategoryToTheBottom(1));
        Category a3 = tree.getAt(1);
        assertEquals(4, a3.getId());
        assertEquals(4, a3.left);
        assertEquals(5, a3.right);
        Category a2 = tree.getAt(2);
        assertEquals(3, a2.getId());
        assertEquals(6, a2.left);
        assertEquals(7, a2.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithChildrenToTopCorrectly() {
        CategoryTree<Category> tree = this.tree;
        assertTrue(tree.moveCategoryToTheTop(1));
        Category a = tree.getAt(1);
        assertEquals(1, a.getId());
        assertEquals(9, a.left);
        assertEquals(16, a.right);
        Category b = tree.getAt(0);
        assertEquals(5, b.getId());
        assertEquals(1, b.left);
        assertEquals(8, b.right);
        Category b1 = b.children.getAt(0);
        assertEquals(6, b1.getId());
        assertEquals(2, b1.left);
        assertEquals(7, b1.right);
        Category a1 = a.children.getAt(0);
        assertEquals(2, a1.getId());
        assertEquals(10, a1.left);
        assertEquals(11, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldMoveCategoryWithChildrenToBottomCorrectly() {
        CategoryTree<Category> tree = this.tree;
        assertTrue(tree.moveCategoryToTheBottom(0));
        Category a = tree.getAt(2);
        assertEquals(1, a.getId());
        assertEquals(11, a.left);
        assertEquals(18, a.right);
        Category b = tree.getAt(0);
        assertEquals(5, b.getId());
        assertEquals(1, b.left);
        assertEquals(8, b.right);
        Category b1 = b.children.getAt(0);
        assertEquals(6, b1.getId());
        assertEquals(2, b1.left);
        assertEquals(7, b1.right);
        Category a1 = a.children.getAt(0);
        assertEquals(2, a1.getId());
        assertEquals(12, a1.left);
        assertEquals(13, a1.right);
        assertTypesOfAllNodes();
    }

    @Test
    public void shouldSortByTitle() {
        CategoryTree<Category> tree = this.tree;
        assertTrue(tree.sortByTitle());
        Category c = tree.getAt(0);
        assertEquals(9, c.getId());
        Category b = tree.getAt(1);
        assertEquals(5, b.getId());
        Category a = tree.getAt(2);
        assertEquals(1, a.getId());
        assertTypesOfAllNodes();
    }
}
