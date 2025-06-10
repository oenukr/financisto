package ru.orangesoftware.financisto.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.activity.ActivityLayout.ActivityNode;
import ru.orangesoftware.financisto.db.entity.CategoryEntity;
import ru.orangesoftware.financisto.utils.Utils; // For getEmptyResId()

public class CategorySelector {

    public interface CategorySelectorListener {
        void onCategorySelected(@Nullable CategoryEntity category, @Nullable String categoryPath, boolean selectLast);
    }

    public enum SelectorType {
        TRANSACTION, FILTER
    }

    private final Activity activity;
    private final ActivityLayout x; // Used for creating UI nodes
    private ActivityNode node; // The main UI node for category display
    private TextView categoryText; // Displays selected category path
    private ImageView categoryIcon; // Displays icon for selected category (optional)
    private AutoCompleteTextView categoryFilter; // AutoComplete field
    private CategorySelectorListener listener;

    private List<CategoryEntity> categoryTree = Collections.emptyList();
    private Map<Long, String> categoryDisplayPathMap = Collections.emptyMap();

    // For the selection dialog
    private ArrayAdapter<String> dialogCategoryAdapter;
    private List<String> categoryDisplayListForDialog = new ArrayList<>();
    private List<CategoryEntity> categoriesForDialogList = new ArrayList<>(); // Parallel list to map dialog index to CategoryEntity

    // For AutoCompleteTextView
    private ArrayAdapter<String> newAutoCompleteAdapter;
    private List<String> autoCompleteDisplayList = new ArrayList<>();


    private final SelectorType selectorType;
    private final long excludingSubTreeId;
    private boolean showNoCategory;
    private boolean showSplitCategory = true;

    private long selectedCategoryId = CategoryEntity.NO_CATEGORY_ID;
    private String selectedCategoryPath = "";

    public static final long NO_CATEGORY_ID_CONSTANT = CategoryEntity.NO_CATEGORY_ID;
    public static final long SPLIT_CATEGORY_ID_CONSTANT = CategoryEntity.SPLIT_CATEGORY_ID;

    public CategorySelector(Activity activity, ActivityLayout x, long exclSubTreeId, SelectorType selectorType) {
        this.activity = activity;
        this.x = x;
        this.excludingSubTreeId = exclSubTreeId;
        this.selectorType = selectorType;
        this.showNoCategory = (selectorType == SelectorType.FILTER);
        this.selectedCategoryPath = activity.getString(getEmptyResId());
    }

    public void setListener(CategorySelectorListener listener) {
        this.listener = listener;
    }

    public void createNode(LinearLayout layout, SelectorType type) {
        // Assuming addListNodeIconFilter returns a node that contains R.id.label, R.id.icon, R.id.filter_text
        node = x.addListNodeIconFilter(layout, R.id.category, R.string.category, getEmptyResId());
        if (node != null && node.getView() != null) {
            categoryText = node.getView().findViewById(R.id.label);
            categoryIcon = node.getView().findViewById(R.id.icon);
            categoryFilter = node.getView().findViewById(R.id.filter_text);
            if (categoryFilter != null) {
                 categoryFilter.setHint(R.string.filter_categories_hint);
                 categoryFilter.setVisibility(View.VISIBLE);
                 showFilter();
            }
        }
        if (type == SelectorType.FILTER) {
            setShowNoCategory(true);
        }
        if (categoryText != null) {
            categoryText.setText(selectedCategoryPath);
        }
        showHideMinusBtn(selectedCategoryId != NO_CATEGORY_ID_CONSTANT && selectedCategoryId != SPLIT_CATEGORY_ID_CONSTANT);
    }

    public void setCategoryData(List<CategoryEntity> newCategoryTree, Map<Long, String> newCategoryDisplayPathMap) {
        this.categoryTree = newCategoryTree != null ? new ArrayList<>(newCategoryTree) : Collections.emptyList();
        this.categoryDisplayPathMap = newCategoryDisplayPathMap != null ? new HashMap<>(newCategoryDisplayPathMap) : Collections.emptyMap();

        this.categoryDisplayListForDialog.clear();
        this.categoriesForDialogList.clear();
        this.autoCompleteDisplayList.clear();

        String noCategoryStr = activity.getString(R.string.no_category);
        String splitCategoryStr = activity.getString(R.string.split_category_title);

        if (showNoCategory) {
            CategoryEntity noCategory = new CategoryEntity();
            noCategory.setId(NO_CATEGORY_ID_CONSTANT);
            noCategory.setTitle(noCategoryStr);
            this.categoriesForDialogList.add(noCategory);
            this.categoryDisplayListForDialog.add(noCategory.getTitle());
            this.autoCompleteDisplayList.add(noCategory.getTitle());
        }

        if (showSplitCategory && selectorType == SelectorType.TRANSACTION) {
            CategoryEntity splitCategory = new CategoryEntity();
            splitCategory.setId(SPLIT_CATEGORY_ID_CONSTANT);
            splitCategory.setTitle(splitCategoryStr);
            this.categoriesForDialogList.add(splitCategory);
            this.categoryDisplayListForDialog.add(splitCategory.getTitle());
            this.autoCompleteDisplayList.add(splitCategory.getTitle());
        }

        for (CategoryEntity cat : this.categoryTree) {
            if (cat.getId() == NO_CATEGORY_ID_CONSTANT && !showNoCategory) continue;
            if (cat.getId() == SPLIT_CATEGORY_ID_CONSTANT && !showSplitCategory) continue;
            if (excludingSubTreeId > 0 && isDescendantOrSelf(cat, excludingSubTreeId)) continue;

            String displayPath = this.categoryDisplayPathMap.getOrDefault(cat.getId(), cat.getTitle());
            this.categoryDisplayListForDialog.add(displayPath);
            this.categoriesForDialogList.add(cat);
            this.autoCompleteDisplayList.add(displayPath);
        }
        Collections.sort(this.autoCompleteDisplayList);

        if (activity != null) {
            if (this.dialogCategoryAdapter == null) {
                this.dialogCategoryAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_single_choice, categoryDisplayListForDialog);
            } else {
                this.dialogCategoryAdapter.clear();
                this.dialogCategoryAdapter.addAll(categoryDisplayListForDialog);
                this.dialogCategoryAdapter.notifyDataSetChanged();
            }

            if (this.newAutoCompleteAdapter == null) {
                this.newAutoCompleteAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, autoCompleteDisplayList);
            } else {
                this.newAutoCompleteAdapter.clear();
                this.newAutoCompleteAdapter.addAll(autoCompleteDisplayList);
                this.newAutoCompleteAdapter.notifyDataSetChanged();
            }
             if (categoryFilter != null && categoryFilter.getAdapter() == null) {
                categoryFilter.setAdapter(newAutoCompleteAdapter);
            }
        }

        selectCategory(this.selectedCategoryId, false);
    }

    private boolean isDescendantOrSelf(CategoryEntity cat, long parentIdToExclude) {
        if (cat.getId() == parentIdToExclude) return true;
        // Proper check needs lft/rgt values from CategoryEntity
        return false;
    }

    public void pickCategory() {
        if (activity == null || dialogCategoryAdapter == null || categoriesForDialogList.isEmpty()) return;
        if (dialogCategoryAdapter.getCount() == 0) return;

        int currentSelectionIndex = 0;
        for (int i = 0; i < categoriesForDialogList.size(); i++) {
            if (categoriesForDialogList.get(i).getId() == selectedCategoryId) {
                currentSelectionIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(activity)
            .setTitle(R.string.select_category)
            .setSingleChoiceItems(dialogCategoryAdapter, currentSelectionIndex, (dialog, which) -> {
                if (which >= 0 && which < categoriesForDialogList.size()) {
                    CategoryEntity selected = categoriesForDialogList.get(which);
                    // Call onSelectedId which then calls selectCategory
                     if (activity instanceof AbstractTransactionActivity) {
                         ((AbstractTransactionActivity) activity).onSelectedId(R.id.category, selected.getId());
                    } else if (activity instanceof AbstractListActivity) { // Or other parent types
                        // ((AbstractListActivity) activity).onSelectedId(R.id.category, selected.getId());
                    }
                    // selectCategory(selected.getId(), true); // Direct call also possible
                }
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public void showFilter() {
        if (categoryFilter != null) {
            categoryFilter.setVisibility(View.VISIBLE);
            categoryFilter.requestFocus();
            initAutoCompleteFilter();
        }
    }

    private void initAutoCompleteFilter() {
        if (categoryFilter == null || activity == null) return;

        if (newAutoCompleteAdapter == null) { // Ensure adapter is initialized
             newAutoCompleteAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line, autoCompleteDisplayList);
        }
        categoryFilter.setAdapter(newAutoCompleteAdapter);
        categoryFilter.setOnItemClickListener((parent, view, position, id) -> {
            String selectedPath = (String) parent.getItemAtPosition(position);
            long foundCategoryId = findCategoryByPath(selectedPath);
            // Notify the activity (listener) about the selection
            if (activity instanceof AbstractActivity) { // Check if activity can handle onSelectedId
                 ((AbstractActivity) activity).onSelectedId(R.id.category, foundCategoryId);
            }
            categoryFilter.setText("");
            categoryFilter.setVisibility(View.GONE);
        });
         categoryFilter.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && categoryFilter.getAdapter() == null) {
                categoryFilter.setAdapter(newAutoCompleteAdapter);
            } else if (!hasFocus) {
                categoryFilter.setVisibility(View.GONE);
            }
        });
    }

    private long findCategoryByPath(String selectedPath) {
        String noCategoryStr = activity.getString(getEmptyResId());
        String splitCategoryStr = activity.getString(R.string.split_category_title);

        if (selectedPath.equals(noCategoryStr)) return NO_CATEGORY_ID_CONSTANT;
        if (selectedPath.equals(splitCategoryStr)) return SPLIT_CATEGORY_ID_CONSTANT;

        for (Map.Entry<Long, String> entry : categoryDisplayPathMap.entrySet()) {
            if (entry.getValue().equals(selectedPath)) {
                return entry.getKey();
            }
        }
         for (CategoryEntity cat : categoryTree) { // Fallback to title match
            if (cat.getTitle().equalsIgnoreCase(selectedPath)) {
                return cat.getId();
            }
        }
        return NO_CATEGORY_ID_CONSTANT;
    }

    public void selectCategory(long categoryId) {
        selectCategory(categoryId, true);
    }

    public void selectCategory(long categoryId, boolean selectLast) {
        this.selectedCategoryId = categoryId;

        CategoryEntity entityForCallback = null;
        String pathToDisplay;

        if (categoryId == NO_CATEGORY_ID_CONSTANT) {
            pathToDisplay = activity.getString(getEmptyResId());
            showHideMinusBtn(false);
        } else if (categoryId == SPLIT_CATEGORY_ID_CONSTANT) {
            entityForCallback = new CategoryEntity();
            entityForCallback.setId(SPLIT_CATEGORY_ID_CONSTANT);
            entityForCallback.setTitle(activity.getString(R.string.split_category_title));
            pathToDisplay = entityForCallback.getTitle();
            showHideMinusBtn(true);
        } else {
            entityForCallback = findInDialogListOrTree(categoryId);
            if (entityForCallback != null) {
                pathToDisplay = categoryDisplayPathMap.getOrDefault(categoryId, entityForCallback.getTitle());
                showHideMinusBtn(true);
            } else {
                pathToDisplay = activity.getString(getEmptyResId());
                this.selectedCategoryId = NO_CATEGORY_ID_CONSTANT;
                showHideMinusBtn(false);
            }
        }

        this.selectedCategoryPath = pathToDisplay;
        selectCategoryPath(pathToDisplay); // Update UI text

        if (listener != null) {
            listener.onCategorySelected(entityForCallback, pathToDisplay, selectLast);
        }
    }

    public void selectCategoryPath(String path) {
        if (categoryText != null && path != null) {
            categoryText.setText(path);
        } else if (categoryText != null) {
            categoryText.setText(getEmptyResId());
        }

        boolean isNoCategoryOrSpecial = (selectedCategoryId == NO_CATEGORY_ID_CONSTANT );
        boolean isEmptyDisplay = (path == null || path.equals(activity.getString(getEmptyResId())) || path.equals(activity.getString(R.string.no_category)));

        if (selectedCategoryId == SPLIT_CATEGORY_ID_CONSTANT){ // Split always shows minus
            showHideMinusBtn(true);
        } else {
            showHideMinusBtn(!isEmptyDisplay && !isNoCategoryOrSpecial);
        }
    }

    private CategoryEntity findInDialogListOrTree(long categoryId) {
        for(CategoryEntity cat : categoriesForDialogList) {
            if (cat.getId() == categoryId) return cat;
        }
        // If not in dialog list (e.g. not a special item), search main tree
        for (CategoryEntity cat : categoryTree) {
            if (cat.getId() == categoryId) {
                return cat;
            }
        }
        return null;
    }

    public long getSelectedCategoryId() {
        return selectedCategoryId;
    }

    public String getSelectedCategoryPath() {
        return selectedCategoryPath;
    }

    public boolean isSplitCategorySelected() {
        return selectedCategoryId == SPLIT_CATEGORY_ID_CONSTANT;
    }

    public void setShowNoCategory(boolean showNoCategory) {
        this.showNoCategory = showNoCategory;
    }

    public void setShowSplitCategory(boolean showSplitCategory) {
        this.showSplitCategory = showSplitCategory;
    }

    private int getEmptyResId() {
        return selectorType == SelectorType.FILTER ? R.string.all_categories : R.string.no_category;
    }

    private void showHideMinusBtn(boolean show) {
        if (node != null && node.getView() != null) {
            View minusBtn = node.getView().findViewById(R.id.minus_button);
            if (minusBtn != null) minusBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void onDestroy() {
        // Cleanup
    }
}
