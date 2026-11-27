package eu.x45.listen45.ui.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.x45.listen45.R
import eu.x45.listen45.databinding.HistoryItemBinding
import eu.x45.listen45.mappers.toMusic
import eu.x45.listen45.models.HistoryItem
import eu.x45.listen45.ui.HistoryViewModel
import eu.x45.listen45.ui.MusicActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HistoryItemAdapter(
    var items: List<HistoryItem>,
    private val historyViewModel: HistoryViewModel,
) : RecyclerView.Adapter<HistoryItemAdapter.HistoryItemViewHolder>() {

    inner class HistoryItemViewHolder(val binding: HistoryItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = HistoryItemViewHolder(
        HistoryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: HistoryItemViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            val context = root.context
            coverImageView.setImageResource(R.drawable.baseline_album_24)
            titleTextView.text = item.title
            artistTextView.text = item.artists
            root.isLongClickable = true
            root.setOnClickListener {
                Intent(context, MusicActivity::class.java).also {
                    it.putExtra(MusicActivity.MUSIC, item.toMusic())
                    context.startActivity(it)
                }
            }
            root.setOnLongClickListener {
                MaterialAlertDialogBuilder(
                    root.context, R.style.Base_Theme_Listen45_MaterialAlertDialog
                ).setTitle(R.string.remove_history_item_title).setMessage(
                    context.getString(
                        R.string.remove_history_item_message, item.title
                    )
                ).setPositiveButton(R.string.yes) { dialog, _ ->
                    dialog.dismiss()
                    historyViewModel.delete(item)
                }.setNegativeButton(R.string.no) { dialog, _ ->
                    dialog.dismiss()
                }.show()
                true
            }

            likedImageView.visibility = if (item.liked) View.VISIBLE else View.GONE
            unlikedImageView.visibility = if (!item.liked) View.VISIBLE else View.GONE

            likeFrameLayout.setOnClickListener {
                if (item.liked) {
                    historyViewModel.unlike(item)
                } else {
                    historyViewModel.like(item)
                }
            }
        }

    }
}
