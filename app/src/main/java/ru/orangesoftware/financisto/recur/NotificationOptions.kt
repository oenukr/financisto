package ru.orangesoftware.financisto.recur

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.ColorInt
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.LocalizableEnum

private val DEFAULT_SOUND = Settings.System.DEFAULT_NOTIFICATION_URI.toString()
private const val DELIMITER = ";"

class NotificationOptions private constructor(
	var sound: String?,
	var vibration: VibrationPattern,
	var ledColor: LedColor,
) {
	enum class VibrationPattern(
		override val titleId: Int,
		val pattern: LongArray?,
	) : LocalizableEnum {
		OFF(R.string.notification_options_off, null),
		DEFAULT(R.string.notification_options_default, null),
		SHORT(R.string.notification_options_short, longArrayOf(0,200)),
		SHORT_SHORT(R.string.notification_options_2_short, longArrayOf(0,200,200,200)),
		THREE_SHORTS(R.string.notification_options_3_short, longArrayOf(0,200,200,200,200,200)),
		LONG(R.string.notification_options_long, longArrayOf(0,500)),
		LONG_LONG(R.string.notification_options_2_long, longArrayOf(0,500,300,500)),
		THREE_LONG(R.string.notification_options_3_long, longArrayOf(0,500,300,500,300,500));
	}
	
	enum class LedColor(
		override val titleId: Int,
		@ColorInt val color: Int,
	) : LocalizableEnum  {
		OFF(R.string.notification_options_off, Color.BLACK),
		DEFAULT(R.string.notification_options_default, Color.BLACK),
		GREEN(R.string.notification_options_led_green, Color.GREEN),
		BLUE(R.string.notification_options_led_blue, Color.BLUE),
		YELLOW(R.string.notification_options_led_yellow, Color.YELLOW),
		RED(R.string.notification_options_led_red, Color.RED),
		PINK(R.string.notification_options_led_pink, Color.parseColor("#FF00FF"));
	}

	companion object {
		@JvmStatic
		fun createDefault(): NotificationOptions = NotificationOptions(
			DEFAULT_SOUND,
			VibrationPattern.DEFAULT,
			LedColor.DEFAULT,
		)

		@JvmStatic
		fun createOff(): NotificationOptions = NotificationOptions(
			null,
			VibrationPattern.OFF,
			LedColor.OFF,
		)

		@JvmStatic
		fun parse(options: String): NotificationOptions {
			val optionsList = options.split(DELIMITER)
			return NotificationOptions(
				optionsList.first().ifEmpty { null },
				VibrationPattern.valueOf(optionsList[1]),
				LedColor.valueOf(optionsList[2])
			)
		}
	}
	
	fun isDefault(): Boolean {
		return DEFAULT_SOUND == sound && vibration == VibrationPattern.DEFAULT && ledColor == LedColor.DEFAULT
	}
	
	fun isOff(): Boolean {
		return sound == null && vibration == VibrationPattern.OFF && ledColor == LedColor.OFF
	}

	fun stateToString(): String = listOf(sound.orEmpty(), vibration, ledColor).joinToString(
		separator = DELIMITER,
		postfix = DELIMITER,
	)

	fun toInfoString(context: Context): String = context.getString(
		when {
			isDefault() -> R.string.notification_options_default
			isOff() -> R.string.notification_options_off
			else -> R.string.notification_options_custom
		}
	)

	fun getSoundName(context: Context): String = sound?.let {
		val uri: Uri? = Uri.parse(sound)
		return if (Settings.System.DEFAULT_NOTIFICATION_URI.equals(uri)) {
			context.getString(R.string.notification_options_default)
		} else {
			RingtoneManager.getRingtone(context, uri)?.getTitle(context)
				?: context.getString(R.string.notification_options_off)
		}
	} ?: context.getString(R.string.notification_options_off)

	fun apply(notification: Notification) {
		notification.defaults = 0
		if (isOff()) {
			notification.defaults = 0
		} else if (isDefault()) {
			notification.defaults = Notification.DEFAULT_ALL
			enableLights(notification)
		} else {
			applySound(notification)
			applyVibration(notification)
			applyLed(notification)
		}
	}

	private fun applySound(notification: Notification) {
		sound?.also {
			notification.audioStreamType = AudioManager.STREAM_NOTIFICATION
			notification.sound = Uri.parse(it)
		} ?: { notification.sound = null }
	}

	private fun applyVibration(notification: Notification) = when (vibration) {
		VibrationPattern.OFF -> notification.vibrate = null
		VibrationPattern.DEFAULT ->
			notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
		else -> notification.vibrate = vibration.pattern
	}

	private fun applyLed(notification: Notification) = when (ledColor) {
		LedColor.OFF -> notification.ledARGB = 0
		LedColor.DEFAULT -> {
			notification.defaults = notification.defaults or Notification.DEFAULT_LIGHTS
			enableLights(notification)
		}

		else -> {
			notification.ledARGB = ledColor.color
			enableLights(notification)
		}
	}

	private fun enableLights(notification: Notification) {
		notification.flags = notification.flags or Notification.FLAG_SHOW_LIGHTS
		notification.ledOnMS = 200
		notification.ledOffMS = 200
	}

}
