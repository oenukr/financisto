package ru.orangesoftware.financisto.model

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.EntityEnum

enum class ElectronicPaymentType(
	override val titleId: Int,
	override val iconId: Int
) : EntityEnum {
	PAYPAL(R.string.electronic_type_paypal, R.drawable.electronic_type_paypal),
	AMAZON(R.string.electronic_type_amazon, R.drawable.electronic_type_amazon),
	EBAY(R.string.electronic_type_ebay, R.drawable.electronic_type_ebay),
	GOOGLE_WALLET(R.string.electronic_type_google_wallet, R.drawable.electronic_type_google_wallet),
    BITCOIN(R.string.electronic_type_bitcoin, R.drawable.electronic_type_bitcoin),
	ALIPAY(R.string.electronic_type_alipay, R.drawable.electronic_type_alipay),
	WEB_MONEY(R.string.electronic_type_web_money, R.drawable.electronic_type_webmoney),
    YANDEX_MONEY(R.string.electronic_type_yandex_money, R.drawable.electronic_type_yandex_money);
}
