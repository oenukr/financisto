// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;

import java.util.Collections;

public class Lt implements Expression {

	private final String field;
	private final Object value;
	
	Lt(String field, Object value) {
		this.field = field;
		this.value = value;
	}
	
	@Override
	public Selection toSelection(EntityDefinition ed) {
		return new Selection("("+ed.getColumnForField(field)+"<?)", Collections.singletonList(String.valueOf(value)));
	}

}
