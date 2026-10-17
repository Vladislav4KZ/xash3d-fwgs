package su.xash.engine.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.xash.engine.BuildConfig
import su.xash.engine.MainActivity
import su.xash.engine.R
import su.xash.engine.model.Game
import su.xash.engine.util.GameDataDirectory
import su.xash.engine.util.GameDataDirectory.Result
import su.xash.engine.util.Prefs
import java.io.File

class AppSettingsPreferenceFragment() : PreferenceFragmentCompat() {
	private val appPreferences
		get() = requireContext().getSharedPreferences(Prefs.APP_PREFERENCES, Context.MODE_PRIVATE)

	private val gameDirectoryPicker = registerForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri == null)
			return@registerForActivityResult

		handlePickedGameDirectory(uri)
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		preferenceManager.sharedPreferencesName = Prefs.APP_PREFERENCES;
		setPreferencesFromResource(R.xml.app_preferences, rootKey);

		findPreference<GamePathPreference>(Prefs.GAME_PATH)?.apply {
			val gamePath = appPreferences.getString(Prefs.GAME_PATH, null)
			summary = gamePath ?: Prefs.defaultGamePath.absolutePath
			// Nothing to reset when the stored path is the default location.
			isResetEnabled = gamePath != null && File(gamePath) != Prefs.defaultGamePath
			setOnPreferenceClickListener {
				gameDirectoryPicker.launch(null)
				true
			}
			onResetClickListener = { resetGamePath() }
		}

		findPreference<Preference>("crash_logs")?.setOnPreferenceClickListener {
			findNavController().navigate(R.id.action_appSettingsFragment_to_crashLogsFragment)
			true
		}

		findPreference<Preference>("check_updates")?.apply {
			// Auto-update is only compiled into the continuous build; hide the
			// manual check elsewhere, since the updater is a no-op there.
			isVisible = BuildConfig.ENABLE_AUTO_UPDATE
			setOnPreferenceClickListener {
				(requireActivity() as MainActivity).checkForUpdatesManually()
				true
			}
		}
	}

	private fun handlePickedGameDirectory(uri: Uri) {
		val context = requireContext()

		// Resolving the URI and scanning the picked folder for game
		// directories hits the filesystem and can be slow on large folders,
		// so keep it off the main thread.
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) {
				GameDataDirectory.resolve(context, uri)
			}

			val directory = when (result) {
				is Result.Success -> result.directory
				is Result.Failure -> {
					showGameDirectoryError(result.messageRes)
					return@launch
				}
			}

			// The library lists game directories below the selected root. Do
			// not accept a single game's directory as the root itself.
			val isSingleGame = withContext(Dispatchers.IO) {
				Game.checkIfGamedir(directory) != null
			}
			if (isSingleGame) {
				showGameDirectoryError(R.string.game_data_location_select_parent)
				return@launch
			}

			val hasGames = withContext(Dispatchers.IO) {
				directory.listFiles()?.any {
					it.isDirectory && Game.checkIfGamedir(it) != null
				} == true
			}
			if (hasGames)
				saveGamePath(directory)
			else
				confirmGamePathWithoutGames(directory)
		}
	}

	private fun saveGamePath(directory: File) {
		// Picking the default location is the same as resetting it: there is
		// no custom path anymore, so don't remember it in the preferences.
		if (directory == Prefs.defaultGamePath) {
			resetGamePath()
			return
		}

		appPreferences.edit()
			.putString(Prefs.GAME_PATH, directory.absolutePath)
			.apply()

		findPreference<GamePathPreference>(Prefs.GAME_PATH)?.apply {
			summary = directory.absolutePath
			isResetEnabled = true
		}
	}

	private fun resetGamePath() {
		appPreferences.edit()
			.remove(Prefs.GAME_PATH)
			.apply()

		findPreference<GamePathPreference>(Prefs.GAME_PATH)?.apply {
			summary = Prefs.defaultGamePath.absolutePath
			isResetEnabled = false
		}
	}

	private fun confirmGamePathWithoutGames(directory: File) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.game_data_location_empty_title)
			.setMessage(R.string.game_data_location_empty_message)
			.setPositiveButton(R.string.game_data_location_use_anyway) { _, _ ->
				saveGamePath(directory)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showGameDirectoryError(messageRes: Int) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.game_data_location)
			.setMessage(messageRes)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}
}
