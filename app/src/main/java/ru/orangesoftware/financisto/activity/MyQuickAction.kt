package ru.orangesoftware.financisto.activity

import android.content.Context
import android.graphics.Color
import android.graphics.LightingColorFilter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import androidx.core.content.ContextCompat

import greendroid.widget.QuickAction
import ru.orangesoftware.financisto.R

class MyQuickAction(
    ctx: Context,
    @DrawableRes drawableId: Int,
    @StringRes titleId: Int,
) : QuickAction(
    ctx,
    ContextCompat.getDrawable(ctx, drawableId)!!.mutate().apply { colorFilter = LightingColorFilter(
            Color.BLACK,
            ContextCompat.getColor(ctx, R.color.colorPrimary)
        )
    },
    titleId,
)
