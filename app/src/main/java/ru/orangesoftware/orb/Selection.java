// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;

import java.util.List;

public class Selection {
	
	public final String selection;
	public final List<String> selectionArgs;
	
	public Selection(String selection, List<String> selectionArgs) {
		this.selection = selection;
		this.selectionArgs = selectionArgs;
	}

}
