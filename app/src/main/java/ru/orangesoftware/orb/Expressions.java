// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;

public class Expressions {
	
	private Expressions() {}
	
	public static Expression eq(String field, Object value) {
		return new Eq(field, value);
	}
	
	public static Expression and(Expression... ee) {
		return new And(ee);
	}
	
	public static Expression or(Expression e1, Expression e2) {
		return new Or(e1, e2);
	}
	
	public static Expression or(Expression... ee) {
		return new Or(ee);
	}

	public static Expression lte(String field, Object value) {
		return new Lte(field, value);
	}

	public static Expression gte(String field, Object value) {
		return new Gte(field, value);
	}

	public static Expression lt(String field, Object value) {
		return new Lt(field, value);
	}

	public static Expression gt(String field, Object value) {
		return new Gt(field, value);
	}

	public static Expression btw(String field, Object value1, Object value2) {
		return new Btw(field, value1, value2);
	}

    public static Expression like(String field, Object value1) {
        return new Like(field, value1);
    }
}
