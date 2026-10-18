package su.xash.engine.ui.library

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.xash.engine.model.Game
import su.xash.engine.util.Nomedia
import su.xash.engine.util.Prefs
import java.io.File

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
	val installedGames: LiveData<List<Game>> get() = _installedGames
	private val _installedGames = MutableLiveData(emptyList<Game>())

	val isReloading: LiveData<Boolean> get() = _isReloading
	private val _isReloading = MutableLiveData(false)

	val selectedItem: LiveData<Game> get() = _selectedItem
	private val _selectedItem = MutableLiveData<Game>()

	private val appPreferences: SharedPreferences =
		application.getSharedPreferences(Prefs.APP_PREFERENCES, Context.MODE_PRIVATE)

	// At most one scan runs at a time. Starting a scan cancels the previous one,
	// so only the latest scan can ever publish its result.
	private var scanJob: Job? = null

	// Game data root of the running scan, null while idle.
	private var scanningRootPath: String? = null

	// Game data root that installedGames was scanned from, null until the first
	// successful scan.
	private var loadedRootPath: String? = null

	// Set when the files below loadedRootPath may have changed since they were
	// scanned, see markGamesStale().
	private var gamesStale = true

	private fun currentGameRootPath(): String =
		appPreferences.getString(Prefs.GAME_PATH, null)
			?: Prefs.defaultGamePath.absolutePath

	/**
	 * Makes sure the list shows the games of the selected game data location.
	 * Cheap to call whenever the library becomes visible: it does nothing
	 * unless the list has not been loaded yet, the location has changed or the
	 * list was marked stale.
	 */
	fun reloadGamesIfNeeded() {
		val rootPath = currentGameRootPath()

		when {
			rootPath == scanningRootPath -> return
			rootPath != loadedRootPath || gamesStale -> startScan(rootPath)
			else -> cancelScan() // a scan of some other root is obsolete now
		}
	}

	/**
	 * The games may have been added or removed behind our back, e.g. with a
	 * file manager while the app was in the background. The next
	 * reloadGamesIfNeeded() scans again.
	 */
	fun markGamesStale() {
		gamesStale = true
	}

	/** Scans the selected game data location again, e.g. on a pull to refresh. */
	fun reloadGames() {
		val rootPath = currentGameRootPath()

		if (rootPath != scanningRootPath) {
			startScan(rootPath)
		}
	}

	private fun cancelScan() {
		val job = scanJob ?: return

		job.cancel()
		scanJob = null
		scanningRootPath = null
		_isReloading.value = false
	}

	private fun startScan(rootPath: String) {
		scanJob?.cancel()
		scanningRootPath = rootPath
		// Changes that happen from now on are not covered by this scan, so
		// they have to mark the list stale again.
		gamesStale = false
		_isReloading.value = true

		// The scan outlives the fragment that asked for it, so it must not
		// hold on to an Activity context through the Game objects.
		val appContext = getApplication<Application>()
		val previousGames = _installedGames.value.orEmpty()

		val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
			val games = try {
				withContext(Dispatchers.IO) {
					val root = File(rootPath)

					Nomedia.ensureNomedia(root)

					Game.getGames(appContext, root, previousGames)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.e(TAG, "Failed to scan $rootPath", e)
				null
			}

			// A cancelled scan never gets here, so this is the latest scan. On
			// failure the list stays as it was and the next request retries.
			if (games != null) {
				_installedGames.value = games
				loadedRootPath = rootPath
			} else {
				gamesStale = true
			}

			scanJob = null
			scanningRootPath = null
			_isReloading.value = false
		}

		scanJob = job
		job.start()
	}

	fun setSelectedGame(game: Game) {
		_selectedItem.value = game
	}

	fun startEngine(ctx: Context, game: Game) {
		game.startEngine(ctx)
	}

	private companion object {
		const val TAG = "LibraryViewModel"
	}
}
