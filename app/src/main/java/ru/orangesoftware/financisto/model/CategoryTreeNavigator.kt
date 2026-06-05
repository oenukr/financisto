package ru.orangesoftware.financisto.model

import ru.orangesoftware.financisto.db.DatabaseAdapter
import java.util.Stack

class CategoryTreeNavigator @JvmOverloads constructor(
    private val db: DatabaseAdapter,
    @JvmField val excludedTreeId: Long = -1
) {

    companion object {
        const val INCOME_CATEGORY_ID: Long = -101
        const val EXPENSE_CATEGORY_ID: Long = -102
    }

    private val categoriesStack = Stack<CategoryTree<Category>>()

    @JvmField
    var categories: CategoryTree<Category> = db.getCategoriesTreeWithoutSubTree(excludedTreeId, false)
    
    @JvmField
    var selectedCategoryId: Long = 0

    init {
        val noCategory = db.getCategoryWithParent(Category.NO_CATEGORY_ID)
        tagCategories(noCategory)
    }

    fun selectCategory(selectedCategoryId: Long) {
        val map = categories.asMap()
        val selectedCategory = map[selectedCategoryId]
        if (selectedCategory != null) {
            val path = Stack<Long>()
            var parent = selectedCategory.parent
            while (parent != null) {
                path.push(parent.id)
                parent = parent.parent
            }
            while (!path.isEmpty()) {
                navigateTo(path.pop())
            }
            this.selectedCategoryId = selectedCategoryId
        }
    }

    fun tagCategories(parent: Category) {
        if (!categories.isEmpty() && categories.getAt(0).id != parent.id) {
            val copy = Category()
            copy.id = parent.id
            copy.title = parent.title
            if (parent.isIncome) {
                copy.makeThisCategoryIncome()
            }
            categories.insertAtTop(copy)
        }
        val sb = StringBuilder()
        for (c in categories) {
            if (c.tag == null && c.hasChildren()) {
                sb.setLength(0)
                val children = c.children
                for (child in children) {
                    if (sb.isNotEmpty()) {
                        sb.append(",")
                    }
                    sb.append(child.title)
                }
                c.tag = sb.toString()
            }
        }
    }

    fun goBack(): Boolean {
        if (!categoriesStack.isEmpty()) {
            val selectedCategory = findCategory(selectedCategoryId)
            if (selectedCategory != null) {
                selectedCategoryId = selectedCategory.parentId
            }
            categories = categoriesStack.pop()
            return true
        }
        return false
    }

    fun canGoBack(): Boolean {
        return !categoriesStack.isEmpty()
    }

    fun navigateTo(categoryId: Long): Boolean {
        val selectedCategory = findCategory(categoryId)
        if (selectedCategory != null) {
            selectedCategoryId = selectedCategory.id
            if (selectedCategory.hasChildren()) {
                categoriesStack.push(categories)
                categories = selectedCategory.children
                tagCategories(selectedCategory)
                return true
            }
        }
        return false
    }

    private fun findCategory(categoryId: Long): Category? {
        for (category in categories) {
            if (category.id == categoryId) {
                return category
            }
        }
        return null
    }

    fun isSelected(categoryId: Long): Boolean {
        return selectedCategoryId == categoryId
    }

    fun getSelectedRoots(): List<Category> {
        return categories.roots
    }

    fun addSplitCategoryToTheTop() {
        val splitCategory = db.getCategoryWithParent(Category.SPLIT_CATEGORY_ID)
        categories.insertAtTop(splitCategory)
    }

    fun separateIncomeAndExpense() {
        val newCategories = CategoryTree<Category>()
        val income = Category()
        income.id = INCOME_CATEGORY_ID
        income.makeThisCategoryIncome()
        income.title = "<INCOME>"
        
        val expense = Category()
        expense.id = EXPENSE_CATEGORY_ID
        expense.makeThisCategoryExpense()
        expense.title = "<EXPENSE>"
        
        for (category in categories) {
            if (category.id <= 0) {
                newCategories.add(category)
            } else {
                if (category.isIncome) {
                    income.addChild(category)
                } else {
                    expense.addChild(category)
                }
            }
        }
        if (income.hasChildren()) {
            newCategories.add(income)
        }
        if (expense.hasChildren()) {
            newCategories.add(expense)
        }
        categories = newCategories
    }
}
