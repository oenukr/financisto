package ru.orangesoftware.orb;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.utils.Logger;

public class Query<T> {
	private final Logger logger = new DependenciesHolder().getLogger();

	private final Class<T> clazz;
	private final EntityDefinition ed;
	private final SQLiteDatabase db;

	private final LinkedList<String> orderBy = new LinkedList<>();
	private String where;
	private String[] whereArgs;

	Query(EntityManager em, Class<T> clazz) {
		this.db = em.db();
		this.clazz = clazz;
		this.ed = EntityManager.getEntityDefinitionOrThrow(clazz);
	}

	public Query<T> where(Expression ex) {
		Selection s = ex.toSelection(ed);
		where = s.selection;
		whereArgs = s.selectionArgs.toArray(new String[0]);
		return this;
	}

	public Query<T> sort(Sort...sort) {
		for (Sort s : sort) {
			if (s.asc) {
				asc(s.field);
			} else {
				desc(s.field);
			}
		}
		return this;
	}

	public Query<T> asc(String field) {
		orderBy.add(ed.getColumnForField(field)+" asc");
		return this;
	}

	public Query<T> desc(String field) {
		orderBy.add(ed.getColumnForField(field)+" desc");
		return this;
	}

	public Cursor execute() {
		String query = ed.sqlQuery;
		String where = this.where;
		String[] whereArgs = this.whereArgs;
		StringBuilder sb = new StringBuilder(query);
		if (where != null) {
			sb.append(" where ").append(where);
		}
		if (!orderBy.isEmpty()) {
			sb.append(" order by ");
			boolean addComma = false;
			for (String order : orderBy) {
				if (addComma) {
					sb.append(", ");
				}
				sb.append(order);
				addComma = true;
			}
		}
		query = sb.toString();
		logger.d("QUERY %s: %s", clazz, query);
		logger.d("WHERE: %s", where != null ? where : "");
		logger.d("ARGS: %s", whereArgs != null ? Arrays.toString(whereArgs) : "");
		return db.rawQuery(query, whereArgs);
	}

	public T uniqueResult() {
		try (Cursor c = execute()) {
			if (c.moveToFirst()) {
				return EntityManager.loadFromCursor(c, clazz);
			}
			return null;
		}
	}

	public List<T> list() {
		return readEntityList(execute(), clazz);

	}

	public static <T> List<T> readEntityList(Cursor cursorRes, Class<T> clazz) {
		try (Cursor c = cursorRes) {
			List<T> list = new ArrayList<>();
			while (c.moveToNext()) {
				T e = EntityManager.loadFromCursor(c, clazz);
				list.add(e);
			}
			return list;
		}
	}
}
