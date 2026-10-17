package su.xash.engine.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import su.xash.engine.R
import su.xash.engine.databinding.CardGameBinding
import su.xash.engine.model.Game
import su.xash.engine.ui.library.LibraryViewModel


class GameAdapter(private val libraryViewModel: LibraryViewModel) :
	ListAdapter<Game, GameAdapter.GameViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameAdapter.GameViewHolder {
		val binding = CardGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return GameViewHolder(binding)
	}

	override fun onBindViewHolder(holder: GameAdapter.GameViewHolder, position: Int) {
		return holder.bind(getItem(position))
	}

	fun gameAt(position: Int): Game = getItem(position)

	// The same game data location usually yields the same set of games, but
	// picking a different one can produce a list where every game shares its
	// directory name with a game from the previous location. Compare by the
	// full path, or the recycled cards keep the stale click handlers.
	private class DiffCallback : DiffUtil.ItemCallback<Game>() {
		override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean {
			return oldItem.basedir == newItem.basedir
		}

		override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean {
			return oldItem.basedir == newItem.basedir && oldItem.title == newItem.title
		}

	}

	inner class GameViewHolder(val binding: CardGameBinding) :
		RecyclerView.ViewHolder(binding.root) {
		fun bind(game: Game) {
			binding.apply {
				gameTitle.text = game.title

				if (game.icon != null) {
					gameIcon.setImageBitmap(game.icon)
				} else {
					gameIcon.visibility = View.GONE
				}

				if (game.cover != null) {
					gameCover.setImageBitmap(game.cover)
				} else {
					gameCover.visibility = View.GONE
				}

				settingsButton.setOnClickListener {
					val game = currentGame()

					if (game != null) {
						libraryViewModel.setSelectedGame(game)
					}

					it.findNavController()
						.navigate(R.id.action_libraryFragment_to_gameSettingsFragment)
				}

				root.setOnClickListener { launchEngine() }
				launchButton.setOnClickListener { launchEngine() }
			}
		}

		// Resolve the game at click time: the card is rebound only when DiffUtil
		// sees a change, so a Game captured in bind() may already be stale.
		private fun launchEngine() {
			val game = currentGame()

			if (game != null) {
				libraryViewModel.startEngine(itemView.context, game)
			}
		}

		// adapterPosition rather than bindingAdapterPosition: the latter needs
		// androidx.recyclerview 1.2, while the app resolves an older version.
		private fun currentGame(): Game? {
			val position = adapterPosition

			if (position == RecyclerView.NO_POSITION) {
				return null
			}

			return gameAt(position)
		}
	}
}
