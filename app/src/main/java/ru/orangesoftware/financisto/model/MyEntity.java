/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.model;

import static ru.orangesoftware.orb.EntityManager.DEF_ID_COL;
import static ru.orangesoftware.orb.EntityManager.DEF_TITLE_COL;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Transient;

import ru.orangesoftware.financisto.utils.Utils;

public class MyEntity implements MultiChoiceItem {

	@Id
	@Column(name = DEF_ID_COL)
	public long id = -1;

	@Column(name = DEF_TITLE_COL)
	public String title;

	@Column(name = "is_active")
	public boolean isActive = true;

	@Transient
	public boolean checked;

	public static long[] splitIds(String s) {
		if (Utils.isEmpty(s)) {
			return null;
		}
		String[] a = s.split(",");
		int count = a.length;
		long[] ids = new long[count];
		for (int i=0; i<count; i++) {
			ids[i] = Long.parseLong(a[i]);
		}
		return ids;
	}

	public static <T extends MyEntity> Map<Long, T> asMap(List<T> list) {
		HashMap<Long, T> map = new HashMap<>();
		for (T e : list) {
			map.put(e.id, e);
		}
		return map;
	}

	public static int indexOf(List<? extends MyEntity> entities, long id) {
		if (entities != null) {
			int count = entities.size();
			for (int i=0; i<count; i++) {
				if (entities.get(i).id == id) {
					return i;
				}
			}
		}
		return -1;
	}

	public static <T extends MyEntity> T find(List<T> entities, long id) {
		for (T e : entities) {
			if (e.id == id) {
				return e;
			}
		}
		return null;
	}
	
	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getTitle() {
		return title;
	}
	
	@Override
	public boolean isChecked() {
		return checked;
	}

	@Override
	public void setChecked(boolean checked) {
		this.checked = checked;
	}

	@NonNull
	@Override
	public String toString() {
		return title;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyEntity myEntity = (MyEntity) o;

        return id == myEntity.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

}
