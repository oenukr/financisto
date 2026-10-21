// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.orb;

public interface Expression {
	
	Selection toSelection(EntityDefinition ed);
	
}
