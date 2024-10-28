package ru.orangesoftware.financisto.model

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.EntityEnum

enum class CardIssuer(
	override val titleId: Int,
	override val iconId: Int,
) : EntityEnum {
	DEFAULT(R.string.card_issuer_default, R.drawable.account_type_card_default),
	VISA(R.string.card_issuer_visa, R.drawable.account_type_card_visa),
	VISA_ELECTRON(R.string.card_issuer_electron, R.drawable.account_type_card_visa_electron),
	MASTERCARD(R.string.card_issuer_mastercard, R.drawable.account_type_card_mastercard),
	MAESTRO(R.string.card_issuer_maestro, R.drawable.account_type_card_maestro),
	CIRRUS(R.string.card_issuer_cirrus, R.drawable.account_type_card_cirrus),
	AMEX(R.string.card_issuer_amex, R.drawable.account_type_card_amex),
	JCB(R.string.card_issuer_jcb, R.drawable.account_type_card_jcb),
	DINERS(R.string.card_issuer_diners, R.drawable.account_type_card_diners),
	DISCOVER(R.string.card_issuer_discover, R.drawable.account_type_card_discover),
	MIR(R.string.card_issuer_mir, R.drawable.account_type_card_mir),
	NETS(R.string.card_issuer_nets, R.drawable.account_type_card_nets),
	UNIONPAY(R.string.card_issuer_unionpay, R.drawable.account_type_card_unionpay);
}
