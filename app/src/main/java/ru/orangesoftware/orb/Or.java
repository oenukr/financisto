// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;


class Or extends CompoundExpression {

	Or(Expression... e) {
		super("OR", e);
	}

}
