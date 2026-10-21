// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;


class And extends CompoundExpression {

	And(Expression... e) {
		super("AND", e);
	}

}
