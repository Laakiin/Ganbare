package fr.lkn.ganbare.core.tasks.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import fr.lkn.ganbare.tasks.sync.TaskJsonMirror

/**
 * Initialise le miroir Room <-> JSON au démarrage du process,
 * sans changer ton Application ni tes Activities.
 */
class TaskMirrorInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let {
            try {
                TaskJsonMirror.start(it)
            } catch (e: Throwable) {
                Log.e("TaskMirrorInit", "start failed", e)
            }
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
