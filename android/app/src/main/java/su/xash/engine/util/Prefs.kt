/*
 * Shared preference file and key names used by the launcher, kept here so
 * they are not duplicated as raw strings at every call site.
 */
package su.xash.engine.util

import android.os.Environment
import java.io.File
import java.io.IOException

object Prefs {
	const val APP_PREFERENCES = "app_preferences"
	const val GAME_PATH = "game_path"

	// Game data root used when the user has not picked a custom folder.
	val defaultGamePath: File
		get() = File(Environment.getExternalStorageDirectory(), "xash")

	/**
	 * Whether [path] is the default game data root. Compares resolved paths,
	 * because a picked folder is canonical while the default one may go
	 * through a symlink.
	 */
	fun isDefaultGamePath(path: File): Boolean =
		path.resolved() == defaultGamePath.resolved()

	private fun File.resolved(): File = try {
		canonicalFile
	} catch (e: IOException) {
		absoluteFile
	}
}
