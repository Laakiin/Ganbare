package fr.lkn.ganbare.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fr.lkn.ganbare.MainActivity
import fr.lkn.ganbare.R
import fr.lkn.ganbare.domain.calendar.CalendarEvent
import fr.lkn.ganbare.domain.calendar.CalendarRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NextEventWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateOne(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // UI provisoire pendant le chargement
        val loading = RemoteViews(context.packageName, R.layout.widget_next_event).apply {
            setTextViewText(R.id.widget_title, "Chargement…")
            setTextViewText(R.id.widget_sub, "Ouvrir l’app")
            // Clic -> ouvre l’app (sur les 2 zones cliquables connues)
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_title, openIntent)
            setOnClickPendingIntent(R.id.widget_sub, openIntent)
        }
        appWidgetManager.updateAppWidget(appWidgetId, loading)

        // Travail en arrière-plan
        CoroutineScope(Dispatchers.IO).launch {
            val views = RemoteViews(context.packageName, R.layout.widget_next_event)
            val repo = CalendarRepositoryImpl(context)

            val next = findNextEvent(repo)

            if (next != null) {
                val zone = ZoneId.systemDefault()
                val start = Instant.ofEpochMilli(next.startEpochMillis).atZone(zone)
                val end = Instant.ofEpochMilli(next.endEpochMillis).atZone(zone)
                val fmt = DateTimeFormatter.ofPattern("EEE d MMM • HH:mm–HH:mm", Locale.getDefault())

                val line2 = buildString {
                    append(start.format(fmt))
                    val loc = next.location?.takeIf { it.isNotBlank() }
                    if (loc != null) append(" • ").append(loc)
                }

                views.setTextViewText(R.id.widget_title, next.title.ifBlank { "Sans titre" })
                views.setTextViewText(R.id.widget_sub, line2)
            } else {
                views.setTextViewText(R.id.widget_title, "Aucun événement à venir")
                views.setTextViewText(R.id.widget_sub, "Ouvrir l’app")
            }

            // Clic -> ouvre l’app
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openIntent)
            views.setOnClickPendingIntent(R.id.widget_sub, openIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /** Cherche le premier event dont la fin est > maintenant, en regardant jusqu’à 365 jours. */
    private suspend fun findNextEvent(repo: CalendarRepositoryImpl): CalendarEvent? {
        val nowMs = System.currentTimeMillis()
        val today = LocalDate.now()
        val lookaheadDays = 365

        for (d in 0..lookaheadDays) {
            val date = today.plusDays(d.toLong())
            val list = runCatching { repo.eventsFor(date) }.getOrElse { emptyList() }
            val candidate = list
                .filter { it.endEpochMillis > nowMs }      // pas déjà terminé
                .minByOrNull { it.startEpochMillis }       // le plus tôt de ce jour
            if (candidate != null) return candidate
        }
        return null
    }
}
