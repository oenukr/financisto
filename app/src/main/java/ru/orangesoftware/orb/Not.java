// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;


public class Not implements Expression {
	
	private final Expression e;
	
	public Not(Expression e) {
		this.e = e;
	}

	@Override
	public Selection toSelection(EntityDefinition ed) {
		StringBuilder sb = new StringBuilder();
		sb.append("NOT (");
		Selection s = e.toSelection(ed); 
		sb.append(s.selection);
		sb.append(")");
		return new Selection(sb.toString(), s.selectionArgs);
	}

}
