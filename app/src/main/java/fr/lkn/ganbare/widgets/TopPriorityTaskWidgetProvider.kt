package fr.lkn.ganbare.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import fr.lkn.ganbare.MainActivity
import fr.lkn.ganbare.R
import fr.lkn.ganbare.tasks.io.TaskJson
import fr.lkn.ganbare.tasks.io.TaskJsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Widget "Tâche prioritaire" lisant le JSON partagé.
 */
class TopPriorityTaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateOne(context, appWidgetManager, id)
    }

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val loading = RemoteViews(context.packageName, R.layout.widget_top_task).apply {
            setTextViewText(R.id.widget_title, context.getString(R.string.widget_top_task_loading_title))
            setTextViewText(R.id.widget_sub, context.getString(R.string.widget_top_task_loading_sub))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.widget_title, open)
            setOnClickPendingIntent(R.id.widget_sub, open)
        }
        appWidgetManager.updateAppWidget(appWidgetId, loading)

        CoroutineScope(Dispatchers.IO).launch {
            val all: List<TaskJson> = TaskJsonStore.readAll(context)
            val now = System.currentTimeMillis()

            // candidates = non terminées ; si au moins une a une échéance future, on filtre les passées
            val nonDone = all.filter { !it.done }
            val hasFuture = nonDone.any { it.dueAtMillis != null && it.dueAtMillis >= now }
            val candidates = if (hasFuture) {
                nonDone.filter { it.dueAtMillis != null && it.dueAtMillis >= now }
            } else {
                nonDone
            }

            val top: TaskJson? = candidates.minWithOrNull(
                compareBy<TaskJson> { it.priority }
                    .thenBy { it.dueAtMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title }
            )

            val rv = RemoteViews(context.packageName, R.layout.widget_top_task)
            if (top == null) {
                rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_top_task_empty_title))
                rv.setTextViewText(R.id.widget_sub, context.getString(R.string.widget_top_task_empty_sub))
            } else {
                val locale = Locale.getDefault()
                val zone = ZoneId.systemDefault()
                val dateTxt = top.dueAtMillis?.let {
                    val zdt = Instant.ofEpochMilli(it).atZone(zone)
                    val d = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(zdt)
                    val h = DateTimeFormatter.ofPattern("HH:mm", locale).format(zdt)
                    " • $d • $h"
                } ?: ""
                val prio = context.getString(R.string.widget_top_task_priority_fmt, top.priority)
                rv.setTextViewText(R.id.widget_title, top.title)
                rv.setTextViewText(R.id.widget_sub, "$prio$dateTxt")
            }

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_title, open)
            rv.setOnClickPendingIntent(R.id.widget_sub, open)

            appWidgetManager.updateAppWidget(appWidgetId, rv)
        }
    }
}
